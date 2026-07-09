#!/bin/bash

set -e

echo "========================================"
echo "Building Maven project..."
echo "========================================"

mvn clean package -DskipTests

echo
echo "========================================"
echo "Rebuilding Docker images and starting containers..."
echo "========================================"

docker compose up --build -d

echo
echo "========================================"
echo "Done!"
echo "========================================"
