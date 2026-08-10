#!/bin/bash
set -e
export MSYS_NO_PATHCONV=1

RG="rg-ticketsouq-dev-uae"
ENV="caenv-ticketsouq"

echo "1. Deploying Redis..."
az containerapp create \
  --name redis \
  --resource-group $RG \
  --environment $ENV \
  --image redis:7-alpine \
  --ingress internal \
  --target-port 6379 \
  --min-replicas 1 \
  --max-replicas 1

echo "2. Deploying Elasticsearch..."
az containerapp create \
  --name elasticsearch \
  --resource-group $RG \
  --environment $ENV \
  --image elasticsearch:9.2.3 \
  --ingress internal \
  --target-port 9200 \
  --min-replicas 1 \
  --max-replicas 1 \
  --env-vars "discovery.type=single-node" "ES_JAVA_OPTS=-Xms512m -Xmx512m" "xpack.security.enabled=false"

echo "3. Deploying Kafka (KRaft mode)..."
az containerapp create \
  --name kafka \
  --resource-group $RG \
  --environment $ENV \
  --image confluentinc/cp-kafka:7.6.0 \
  --ingress internal \
  --target-port 9092 \
  --min-replicas 1 \
  --max-replicas 1 \
  --env-vars \
    "KAFKA_NODE_ID=1" \
    "KAFKA_PROCESS_ROLES=broker,controller" \
    "KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093" \
    "KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093" \
    "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092" \
    "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT" \
    "KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER" \
    "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1" \
    "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1" \
    "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1" \
    "CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk"

echo "Redis, Elasticsearch, and Kafka are up and running!"
