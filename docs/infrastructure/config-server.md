# Config Server

Centralised configuration server for the TicketSouq microservices platform. Every service fetches its runtime configuration from this server on startup, enabling environment-specific overrides (local / Docker) without code changes or rebuilds.

---

## Technologies

| Technology | Purpose |
|------------|---------|
| Spring Cloud Config Server | Serves external configuration to all microservices |
| `@EnableConfigServer` | Activates the Config Server capabilities |
| Native profile (classpath) | Stores configuration as YAML files in `config-repo/` |

---

## Configuration Structure

All service configs live under `src/main/resources/config-repo/`, organised by service name:

```
config-repo/
├── application.yaml             # shared defaults for ALL services
├── application-local.yaml       # local overrides
├── application-Docker.yaml      # Docker overrides
├── api-gateway/
│   ├── api-gateway.yaml
│   ├── api-gateway-local.yaml
│   └── api-gateway-Docker.yaml
├── user-service/
├── event-service/
├── venue-service/
├── ticket-service/
├── reservation-service/
├── payment-service/
├── notification-service/
├── audit-service/
├── analytics-service/
└── discovery-server/
```

The `application.yaml` at the root of `config-repo` defines **global defaults** — database connection, Kafka broker, Eureka registration, Swagger, logging, tracing, and metrics — that every service inherits. Services override only the values they differ on.

---

## Defaults Provided by `config-repo/application.yaml`

| Concern | Default Setting (overridable per service) |
|---------|-------------------------------------------|
| Database | PostgreSQL (docker image `postgres:18.4` in docker-compose.yml) via `${database.*}` placeholders |
| JPA | `ddl-auto: validate`, `PostgreSQLDialect` |
| Migrations | Flyway enabled, `baseline-on-migrate: true`, migrations in `classpath:db/migration` |
| Kafka | `JSON` serialisation, `RECORD` ack mode, `earliest` offset, idempotent producer |
| Swagger | `/swagger-ui.html`, `/v3/api-docs` enabled |
| Tracing | Micrometer + Zipkin to Tempo (OTLP export commented out) |
| Metrics | Prometheus `/actuator/prometheus` (read-only endpoint) |
| Logging | Pattern with `spring.application.name`, Loki push |
| Eureka | Register + fetch enabled, 5s lease renewal, 15s expiry (`:125-138`) |
| Feign | Circuit breaker enabled (Resilience4j: 10-window, 50% threshold, 30s open) |
| Profiling | Pyroscope agent enabled |

---

## Key Notes

- **Eureka registration is disabled** for the config-server itself (it starts before the discovery server).
- The `spring-cloud-config-monitor` dependency is commented out in `pom.xml:19-22` — webhook-based config refresh is not currently used.
- Every service references this server via `spring.config.import: "configserver:http://config-server:8888"` in its `application-Docker.yaml` (and `http://localhost:8888` in `application-local.yaml`).
