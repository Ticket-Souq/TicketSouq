#!/bin/bash

set -e


echo "========================================"
echo "Building Maven project..."
echo "========================================"

mvn clean package -DskipTests

echo ""
echo "========================================"
echo "Remove Old images without Database volumes..."
echo "========================================"

docker compose down --remove-orphans --rmi local

echo ""
echo "========================================"
echo "Rebuilding Docker images..."
echo "========================================"

docker compose build --no-cache

echo ""
echo "========================================"
echo "starting containers..."
echo "========================================"

docker compose up -d

echo ""
echo "========================================"
echo "Opening tunnel to your local host... (don't uncomment of you don't have cloudflare) "
echo "========================================"

cloudflared tunnel --url http://localhost:5173

echo ""
echo "========================================"
echo "Done!"
echo "========================================"
