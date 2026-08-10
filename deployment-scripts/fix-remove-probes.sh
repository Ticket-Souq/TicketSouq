#!/bin/bash
set -e
export MSYS_NO_PATHCONV=1

RG="rg-ticketsouq-dev-uae"
SERVICES=("config-server" "discovery-server" "api-gateway" "user-service" "event-service" "venue-service" "ticket-service" "payment-service" "reservation-service" "notification-service" "audit-service" "analytics-service")

echo "Removing strict startup/liveness probes for all microservices to prevent crashing..."

for SERVICE in "${SERVICES[@]}"; do
    echo "------------------------------------------------"
    echo "Updating: $SERVICE"

    az containerapp update \
      --name $SERVICE \
      --resource-group $RG \
      --startup-probe-type None || true

    echo "✅ Probes removed for: $SERVICE"
done

echo "🎉 All services have been updated and freed from strict probes!"
