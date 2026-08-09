$ErrorActionPreference = "Stop"

Write-Host "========================================"
Write-Host "Building Maven project..."
Write-Host "========================================"

# mvn clean package -DskipTests

Write-Host ""
Write-Host "========================================"
Write-Host "Remove Old images without Database volumes..."
Write-Host "========================================"

docker compose down --remove-orphans --rmi local

Write-Host ""
Write-Host "========================================"
Write-Host "Rebuilding Docker images..."
Write-Host "========================================"

docker compose build --no-cache

Write-Host ""
Write-Host "========================================"
Write-Host "starting containers..."
Write-Host "========================================"

docker compose up -d

Write-Host ""
Write-Host "========================================"
Write-Host "Opening tunnel to your local host... (don't uncomment of you don't have cloudflare) "
Write-Host "========================================"

#cloudflared tunnel --url http://localhost:5173

Write-Host ""
Write-Host "========================================"
Write-Host "Done!"
Write-Host "========================================"
