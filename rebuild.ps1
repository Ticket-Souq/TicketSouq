$ErrorActionPreference = "Stop"

Write-Host "========================================"
Write-Host "Building Maven project..."
Write-Host "========================================"

mvn clean package -DskipTests

Write-Host ""
Write-Host "========================================"
Write-Host "Rebuilding Docker images and starting containers..."
Write-Host "========================================"

docker compose up --build -d

Write-Host ""
Write-Host "========================================"
Write-Host "Done!"
Write-Host "========================================"
