param(
    [string]$Origin = "http://127.0.0.1:5173",
    [string]$Protocol = "http2",
    [int]$MaxAttempts = 10,
    [switch]$FixHosts,
    [switch]$Background,
    [int]$MetricsPort = 0
)

$ErrorActionPreference = "Stop"
$logFile = Join-Path $env:TEMP "ticketsouq-cloudflared.log"
$errFile = Join-Path $env:TEMP "ticketsouq-cloudflared.err.log"

# Auto-elevate when -FixHosts is requested but we're not admin
if ($FixHosts) {
    $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) {
        Write-Host "Requesting Administrator privileges to update the hosts file..." -ForegroundColor Yellow
        Write-Host "A User Account Control (UAC) prompt will appear - click Yes." -ForegroundColor Yellow
        $scriptArgs = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Origin `"$Origin`" -Protocol $Protocol -MaxAttempts $MaxAttempts -FixHosts -MetricsPort $MetricsPort"
        if ($Background) { $scriptArgs += " -Background" }
        Start-Process powershell -Verb RunAs -ArgumentList $scriptArgs -Wait
        if ($Background) { Write-Host "Elevated run finished." -ForegroundColor Cyan }
        exit 0
    }
}

# Pick a free metrics port if not specified (avoids conflicts with leftover processes)
function Get-FreePort([int]$Start = 21000, [int]$End = 22999) {
    for ($p = $Start; $p -le $End; $p++) {
        try {
            $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $p)
            $listener.Start()
            $listener.Stop()
            return $p
        } catch { }
    }
    throw "No free port found in range $Start-$End"
}
if ($MetricsPort -eq 0) { $MetricsPort = Get-FreePort }
$metricsUrl = "http://127.0.0.1:$MetricsPort/metrics"

Write-Host "=== TicketSouq Cloudflare Tunnel ===" -ForegroundColor Cyan
Write-Host "Origin:   $Origin" -ForegroundColor Gray
Write-Host "Protocol: $Protocol" -ForegroundColor Gray

# Verify the origin server is actually up
try {
    $check = Invoke-WebRequest -Uri $Origin -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
    Write-Host "Origin OK ($($check.StatusCode))" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Origin $Origin is not responding. Start your dev server first." -ForegroundColor Red
    exit 1
}

# Stop any existing cloudflared process (tolerate elevated leftovers we can't stop)
Get-Process cloudflared -ErrorAction SilentlyContinue | ForEach-Object {
    try {
        Stop-Process -Id $_.Id -Force -ErrorAction Stop
    } catch {
        Write-Host "WARNING: cloudflared PID $($_.Id) is running elevated and could not be stopped." -ForegroundColor Yellow
        Write-Host "         A tunnel may already be running. Run this script as Administrator, then retry." -ForegroundColor Yellow
    }
}
Start-Sleep -Seconds 2

# Functions to extract the tunnel hostname from the metrics endpoint
function Get-TunnelHostname {
    try {
        $m = Invoke-WebRequest -Uri "http://127.0.0.1:$MetricsPort/metrics" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($m.Content -match 'userHostname="https://([a-z0-9\-]+\.trycloudflare\.com)"') {
            return $matches[1]
        }
    } catch { }
    return $null
}

$hostname = $null
for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
    Write-Host "`nAttempt $attempt/$MaxAttempts - starting cloudflared..." -ForegroundColor Yellow

    Remove-Item $logFile, $errFile -ErrorAction SilentlyContinue

    $proc = Start-Process -FilePath "cloudflared" `
        -ArgumentList "tunnel --url $Origin --protocol $Protocol --metrics 127.0.0.1:$MetricsPort --no-autoupdate" `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError $errFile `
        -WindowStyle Hidden `
        -PassThru

    Write-Host "cloudflared running (PID $($proc.Id))..." -ForegroundColor Gray

    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1

        $hostname = Get-TunnelHostname
        if ($hostname) { break }

        # If exited, dump error tail and retry
        if ($proc.HasExited) {
            Write-Host "cloudflared exited (code $($proc.ExitCode)). Tail:" -ForegroundColor Red
            Get-Content $errFile, $logFile -ErrorAction SilentlyContinue | Select-Object -Last 8 | ForEach-Object {
                Write-Host "  $_" -ForegroundColor DarkYellow
            }
            break
        }
    }

    if ($hostname) { break }
}

if (-not $hostname) {
    Write-Host "`nFailed to establish tunnel after $MaxAttempts attempts." -ForegroundColor Red
    Write-Host "Your ISP DNS (163.121.128.x) appears to block Cloudflare. Options:" -ForegroundColor Yellow
    Write-Host "  1. Re-run as Administrator with -FixHosts  (adds hosts entries)" -ForegroundColor Yellow
    Write-Host "  2. Or enable DNS-over-HTTPS in your browser (Chrome: Settings > Security > Use secure DNS)" -ForegroundColor Yellow
    exit 1
}

$url = "https://$hostname"
Write-Host ""
Write-Host "TUNNEL READY" -ForegroundColor Green
Write-Host "URL: $url" -ForegroundColor Green
Write-Host ""

# Resolve the tunnel's edge IP (retries; falls back to Cloudflare's anycast IPs)
function Get-EdgeIp([string]$Hostname) {
    for ($i = 1; $i -le 5; $i++) {
        try {
            $ip = (Resolve-DnsName $Hostname -Server 1.1.1.1 -Type A -ErrorAction Stop |
                Where-Object { $_.Type -eq 'A' } | Select-Object -First 1).IPAddress
            if ($ip) { return $ip }
        } catch { }
        Start-Sleep -Seconds 2
    }
    return "104.16.231.132"
}

$edgeIp = Get-EdgeIp $hostname

# Verify the tunnel works end-to-end (bypasses broken local DNS via edge IP)
$verified = $false
for ($v = 1; $v -le 6; $v++) {
    try {
        $code = & curl.exe -s -o NUL -w "%{http_code}" --max-time 20 --resolve "${hostname}:443:${edgeIp}" "https://$hostname"
        if ($code -eq "200") {
            $verified = $true
            Write-Host "VERIFIED: tunnel returns HTTP 200 - your app is reachable." -ForegroundColor Green
            break
        }
        Write-Host "NOTE: verification returned HTTP $code (edge still warming up, retry $v/6)..." -ForegroundColor Yellow
    } catch {
        Write-Host "NOTE: verification attempt $v failed: $($_.Exception.Message)" -ForegroundColor Yellow
    }
    Start-Sleep -Seconds 3
}
if (-not $verified) {
    Write-Host "NOTE: could not confirm HTTP 200 yet - the tunnel is registered and should work; refresh the page if needed." -ForegroundColor Yellow
}

Write-Host ""

# Fix local DNS blocking via hosts file
if ($FixHosts) {
    $hostsPath = "$env:SystemRoot\System32\drivers\etc\hosts"
    if (-not (Test-Path $hostsPath)) {
        Write-Host "ERROR: hosts file not found!" -ForegroundColor Red
    } else {
        $entry = "$edgeIp $hostname"
        $hosts = Get-Content $hostsPath
        if ($hosts -notcontains $entry) {
            Add-Content -Path $hostsPath -Value $entry
            Write-Host "Added hosts entry: $entry" -ForegroundColor Green
            ipconfig /flushdns | Out-Null
            Write-Host "DNS cache flushed." -ForegroundColor Green
        } else {
            Write-Host "Hosts entry already present: $entry" -ForegroundColor Green
        }
    }
} else {
    # Not using -FixHosts: check if the browser will even be able to resolve the URL
    $resolvable = $false
    try {
        $null = Resolve-DnsName $hostname -ErrorAction Stop
        $resolvable = $true
    } catch { }
    if (-not $resolvable) {
        Write-Host "NOTE: Your ISP DNS blocks trycloudflare.com (that's why the URL returns DNS_PROBE_FINISHED_NXDOMAIN)." -ForegroundColor Yellow
        $fix = Read-Host "Fix it now by adding a hosts entry? (requires admin, shows a UAC prompt) [Y/n]"
        if ($fix -ne "n" -and $fix -ne "N") {
            $hostsPath = "$env:SystemRoot\System32\drivers\etc\hosts"
            $entry = "$edgeIp $hostname"
            $snippet = @(
                "Add-Content -Path '$hostsPath' -Value '$entry'"
                "ipconfig /flushdns | Out-Null"
            ) -join "; "
            $tmpScript = Join-Path $env:TEMP "ticketsouq-fix-hosts.ps1"
            Set-Content -Path $tmpScript -Value $snippet
            try {
                Start-Process powershell -Verb RunAs -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$tmpScript`"" -Wait -ErrorAction Stop
                $applied = @(Get-Content $hostsPath -ErrorAction SilentlyContinue) -contains $entry
                if ($applied) {
                    Write-Host "Hosts entry added: $entry" -ForegroundColor Green
                } else {
                    Write-Host "Hosts entry could not be verified. Alternative fixes:" -ForegroundColor Yellow
                    Write-Host "  1. Run this as Administrator:  .\start-tunnel.ps1 -FixHosts" -ForegroundColor Yellow
                    Write-Host "  2. Or enable Chrome Secure DNS:  Settings > Privacy and security > Security > Use secure DNS > Cloudflare" -ForegroundColor Yellow
                }
            } catch {
                Write-Host "Elevation cancelled or failed. Alternative fixes:" -ForegroundColor Yellow
                Write-Host "  1. Run this as Administrator:  .\start-tunnel.ps1 -FixHosts" -ForegroundColor Yellow
                Write-Host "  2. Or enable Chrome Secure DNS:  Settings > Privacy and security > Security > Use secure DNS > Cloudflare" -ForegroundColor Yellow
            }
        } else {
            Write-Host "Skipping DNS fix." -ForegroundColor Gray
            Write-Host "Enable Chrome Secure DNS (Settings > Privacy and security > Security > Use secure DNS > Cloudflare) and reload the URL." -ForegroundColor Yellow
        }
    }
    Write-Host "Opening the URL in your default browser..." -ForegroundColor Yellow
}

# Open the tunnel in the default browser automatically
try {
    Start-Process $url -ErrorAction Stop
    Write-Host "Browser opened: $url" -ForegroundColor Green
} catch {
    Write-Host "Could not auto-open the browser. Open manually: $url" -ForegroundColor Yellow
}

if ($Background) {
    # Detached mode: print URL and exit; tunnel keeps running until cloudflared window closes
    Write-Host "Running in background. Close the cloudflared window (or run 'Get-Process cloudflared | Stop-Process') to stop." -ForegroundColor Cyan
    exit 0
}

# ---------- Foreground live view: stream cloudflared logs in front of you ----------
Write-Host ""
Write-Host "--- cloudflared output (press Ctrl+C to stop the tunnel) ---" -ForegroundColor Cyan

$seen = @{}
try {
    while ($true) {
        foreach ($f in @($errFile, $logFile)) {
            if (Test-Path $f) {
                $key = $f
                if (-not $seen.ContainsKey($key)) { $seen[$key] = 0 }
                $lines = @(Get-Content $f -ErrorAction SilentlyContinue)
                for ($i = $seen[$key]; $i -lt $lines.Count; $i++) {
                    Write-Host "  $($lines[$i])" -ForegroundColor DarkGray
                }
                $seen[$key] = $lines.Count
            }
        }

        if ($proc.HasExited) {
            Write-Host ""
            Write-Host "cloudflared exited. Tunnel is down." -ForegroundColor Red
            break
        }

        Start-Sleep -Milliseconds 500
    }
} finally {
    Write-Host ""
    Write-Host "Stopping tunnel..." -ForegroundColor Cyan
    Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force
    Write-Host "Tunnel stopped." -ForegroundColor Cyan
}