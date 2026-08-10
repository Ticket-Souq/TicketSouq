#!/bin/bash
set -e
export MSYS_NO_PATHCONV=1

RG="rg-ticketsouq-dev-uae"
ENV="caenv-ticketsouq"
ACR_NAME="tsacr7059"
ACR_SERVER="tsacr7059.azurecr.io"
VERSION="v1.0"

echo "1. Fetching ACR Credentials..."
ACR_USER=$(az acr credential show -n $ACR_NAME --query "username" -o tsv)
ACR_PASS=$(az acr credential show -n $ACR_NAME --query "passwords[0].value" -o tsv)

SERVICES=("user-service" "event-service" "venue-service" "ticket-service" "payment-service" "reservation-service" "notification-service" "audit-service" "analytics-service")

echo "2. Deploying 9 Business Services..."
for SERVICE in "${SERVICES[@]}"; do
    echo "------------------------------------------------"
    echo "Deploying: $SERVICE"

    PORT=8080
    EXTRA_ENV=""

    case $SERVICE in
        "user-service") PORT=8081 ;;
        "event-service") PORT=8082; EXTRA_ENV="SEARCH_ENGINE=ES" ;;
        "ticket-service") PORT=8083 ;;
        "venue-service") PORT=8084 ;;
        "reservation-service") PORT=8085 ;;
        "payment-service") PORT=8086; EXTRA_ENV="PAYMENT_PROVIDER=MOCK MOCK_SUCCESS_RATE=100 STRIPE_SECRET=sk_test STRIPE_PUBLISHABLE=pk_test STRIPE_WEBHOOK=whsec_test" ;;
        "notification-service") PORT=8087; EXTRA_ENV="EMAIL=test@test.com EMAIL_PASSWORD=pass EMAIL_PROVIDER=mock FRONTEND_URL=http://localhost:5173/login BRAND_NAME=Ticketaty SUPPORT_EMAIL=support@ticketaty.com" ;;
        "audit-service") PORT=8088 ;;
        "analytics-service") PORT=8089 ;;
    esac

    az containerapp create \
      --name $SERVICE \
      --resource-group $RG \
      --environment $ENV \
      --image $ACR_SERVER/$SERVICE:$VERSION \
      --registry-server $ACR_SERVER \
      --registry-username $ACR_USER \
      --registry-password $ACR_PASS \
      --ingress internal \
      --target-port $PORT \
      --min-replicas 1 \
      --max-replicas 1 \
      --env-vars "SPRING_PROFILES_ACTIVE=Docker" $EXTRA_ENV

    echo "✅ $SERVICE is up and running!"
done

echo "🎉 All 9 Business Services have been deployed successfully!"
