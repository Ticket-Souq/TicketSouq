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

echo "2. Deploying config-server..."
az containerapp create \
  --name config-server \
  --resource-group $RG \
  --environment $ENV \
  --image $ACR_SERVER/config-server:$VERSION \
  --registry-server $ACR_SERVER \
  --registry-username $ACR_USER \
  --registry-password $ACR_PASS \
  --ingress internal \
  --target-port 8888 \
  --min-replicas 1 \
  --max-replicas 1 \
  --env-vars "SPRING_PROFILES_ACTIVE=Docker,native"

echo "✅ config-server is up and running!"

echo "3. Deploying discovery-server..."
az containerapp create \
  --name discovery-server \
  --resource-group $RG \
  --environment $ENV \
  --image $ACR_SERVER/discovery-server:$VERSION \
  --registry-server $ACR_SERVER \
  --registry-username $ACR_USER \
  --registry-password $ACR_PASS \
  --ingress internal \
  --target-port 8761 \
  --min-replicas 1 \
  --max-replicas 1 \
  --env-vars "SPRING_PROFILES_ACTIVE=Docker"

echo "✅ discovery-server is up and running!"
echo "🎉 Core Spring infrastructure is successfully deployed!"
