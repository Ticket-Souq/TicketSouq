$ErrorActionPreference = "Stop"

Write-Host "========================================"
Write-Host "Building Maven project..."
Write-Host "========================================"

mvn clean package -DskipTests

Write-Host ""
Write-Host "========================================"
Write-Host "Remove Old images without Database volumes..."
Write-Host "========================================"

docker compose down --remove-orphans --rmi local

# warning adding --volumes in the end remove the data also

Write-Host ""
Write-Host "========================================"
Write-Host "Rebuilding Docker images..."
Write-Host "========================================"

docker compose build --no-cache

Write-Host ""
Write-Host "========================================"
Write-Host "starting containers..."
Write-Host "========================================"

docker compose up --build -d

Write-Host ""
Write-Host "========================================"
Write-Host "Done!"
Write-Host "========================================"
