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

echo "2. Deploying api-gateway (External Ingress)..."
az containerapp create \
  --name api-gateway \
  --resource-group $RG \
  --environment $ENV \
  --image $ACR_SERVER/api-gateway:$VERSION \
  --registry-server $ACR_SERVER \
  --registry-username $ACR_USER \
  --registry-password $ACR_PASS \
  --ingress external \
  --target-port 8080 \
  --min-replicas 1 \
  --max-replicas 1 \
  --env-vars \
    "SPRING_PROFILES_ACTIVE=Docker" \
    "JWT_SECRET=your_jwt_secret_key_here_very_secure_and_long_123456" \
    "RATE_LIMIT=2000"

echo "✅ api-gateway is up and running!"
echo "🎉 Congratulations! Your entire backend architecture is now live on Azure!"
