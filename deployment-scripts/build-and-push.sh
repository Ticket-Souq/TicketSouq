#!/bin/bash
set -e
export MSYS_NO_PATHCONV=1

ACR_NAME="tsacr7059.azurecr.io"
VERSION="v1.0"

SERVICES=("config-server" "discovery-server" "api-gateway" "user-service" "event-service" "venue-service" "ticket-service" "payment-service" "reservation-service" "notification-service" "audit-service" "analytics-service")

echo "Starting Build and Push process for Microservices..."

for SERVICE in "${SERVICES[@]}"; do
    echo "------------------------------------------------"
    echo "Building Docker image for: $SERVICE"

    docker build \
      --build-arg JAR_FILE="${SERVICE}/target/*.jar" \
      -t "${ACR_NAME}/${SERVICE}:${VERSION}" \
      -f Dockerfile .

    echo "Pushing ${SERVICE} to ACR..."
    docker push "${ACR_NAME}/${SERVICE}:${VERSION}"

    echo "✅ Successfully pushed ${SERVICE}"
done

echo "All images have been built and pushed to $ACR_NAME!"
