# Discovery Server

Netflix Eureka service registry for the TicketSouq platform. All microservices register themselves with this server on startup, enabling client-side load balancing via Spring Cloud LoadBalancer and Feign clients.

---

## Technologies

| Technology | Purpose | Evidence |
|------------|---------|----------|
| Spring Cloud Netflix Eureka Server | Service registry & discovery | `pom.xml:17` — dependency `spring-cloud-starter-netflix-eureka-server` |
| `@EnableEurekaServer` | Activates the Eureka Server | `DiscoveryServerApplication.java:8` |
| Spring Cloud Config Client | Fetches its own config from `config-server` | `pom.xml:21` — dependency `spring-cloud-starter-config` |

---

## How Services Register

Every other microservice inherits the following Eureka defaults from `config-repo/application.yaml`:

```yaml
eureka:
  client:
    service-url:
      defaultZone: ${EurekaLink}           # resolves to EurekaOrigin/eureka/
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${spring.application.instance_id:${random.value}}
    lease-renewal-interval-in-seconds: 5
    lease-expiration-duration-in-seconds: 15
```

Key points:
- **Lease renewal** every 5 seconds; eviction after 15 seconds of missed heartbeats.
- **`prefer-ip-address: true`** — useful in Docker environments where hostnames may not resolve.
- **Random instance ID** appended to allow multiple instances of the same service.

---

## Key Notes

- The discovery-server is **one of the first services to start**, alongside config-server.
- All services (except config-server and discovery-server) register with Eureka on startup.
- The discovery-server itself opts out of registration and registry fetching (`config-repo/discovery-server/discovery-server.yaml`: `register-with-eureka: false`, `fetch-registry: false`).
- `discovery-server-local.yaml` and `discovery-server-docker.yaml` exist in `config-repo/discovery-server/` but are empty (no overrides needed). Note the lowercase `-docker` suffix — unlike other services which use `-Docker.yaml`.
- Feign clients use the registered service names (e.g., `user-service`, `event-service`) for inter-service HTTP calls.
