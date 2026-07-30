# Config Server

Centralised configuration server for the TicketSouq microservices platform. Every service fetches its runtime configuration from this server on startup, enabling environment-specific overrides (local / Docker) without code changes or rebuilds.

---

## Technologies

| Technology | Purpose | Evidence |
|------------|---------|----------|
| Spring Cloud Config Server | Serves external configuration to all microservices | `pom.xml:17` — dependency `spring-cloud-config-server` |
| `@EnableConfigServer` | Activates the Config Server capabilities | `ConfigServerApplication.java:8` |
| Native profile (classpath) | Stores configuration as YAML files in `config-repo/` | `application.yaml:8-17` — `spring.profiles.active: native` |

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

**Evidence source:** `config-repo/application.yaml` (lines 1–119) — contains PostgreSQL, Kafka, Springdoc, Loki/Zipkin tracing, Prometheus metrics, and Eureka client defaults.

---

## Defaults Provided by `config-repo/application.yaml`

| Concern | Default Setting (overridable per service) |
|---------|-------------------------------------------|
| Database | PostgreSQL 16 via `${database.*}` placeholders |
| JPA | `ddl-auto: update`, `PostgreSQLDialect` |
| Kafka | `JSON` serialisation, `RECORD` ack mode, `earliest` offset |
| Swagger | `/swagger-ui.html`, `/v3/api-docs` enabled |
| Tracing | Micrometer + Zipkin to Tempo |
| Metrics | Prometheus `/actuator/prometheus` |
| Logging | Pattern with `spring.application.name`, Loki push |
| Eureka | Register + fetch enabled, 5s lease renewal, 15s expiry |

---

## Server Config

```yaml
server:
  port: 8888
spring:
  application:
    name: config-server
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations:
            - classpath:/config-repo/
            - classpath:/config-repo/{application}/
eureka:
  client:
    register-with-eureka: false   # config-server does NOT register itself
    fetch-registry: false
```

**Evidence sources:** `config-server/.../resources/application.yaml:1-22`.

---

## Key Notes

- **Eureka registration is disabled** for the config-server itself (it starts before the discovery server).
- The `spring-cloud-config-monitor` dependency is commented out in `pom.xml:19-22` — webhook-based config refresh is not currently used.
- Every service references this server via `spring.config.import` in its bootstrap config.
