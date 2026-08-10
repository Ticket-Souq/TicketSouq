#!/bin/bash
set -e
export MSYS_NO_PATHCONV=1

RG="rg-ticketsouq-dev-uae"

SERVICES=("config-server" "discovery-server" "api-gateway" "user-service" "event-service" "venue-service" "ticket-service" "payment-service" "reservation-service" "notification-service" "audit-service" "analytics-service")

echo "Starting to update startup probes and restart all microservices..."

for SERVICE in "${SERVICES[@]}"; do
    echo "------------------------------------------------"
    echo "Updating startup probe for: $SERVICE"

    # تحديد البورت المناسب لكل خدمة بناءً على إعداداتها
    PORT=8080
    case $SERVICE in
        "config-server") PORT=8888 ;;
        "discovery-server") PORT=8761 ;;
        "api-gateway") PORT=8080 ;;
        "user-service") PORT=8081 ;;
        "event-service") PORT=8082 ;;
        "ticket-service") PORT=8083 ;;
        "venue-service") PORT=8084 ;;
        "reservation-service") PORT=8085 ;;
        "payment-service") PORT=8086 ;;
        "notification-service") PORT=8087 ;;
        "audit-service") PORT=8088 ;;
        "analytics-service") PORT=8089 ;;
    esac

    az containerapp update \
      --name $SERVICE \
      --resource-group $RG \
      --startup-probe-tcp-socket-port $PORT \
      --startup-probe-initial-delay 45 \
      --startup-probe-period 10

    echo "✅ Successfully updated and restarted: $SERVICE"
done

echo "🎉 All microservices have been successfully updated with robust startup probes!"
