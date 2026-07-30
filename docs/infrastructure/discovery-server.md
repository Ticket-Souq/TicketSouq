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

## Configuration

```yaml
spring:
  application:
    name: discovery-server
    version: 1.0.0
  config:
    import: optional:file:./profile.properties
```

**Evidence source:** `discovery-server/.../resources/application.yaml:1-8`.

The discovery server reads **profile-specific configuration from the config-server**. Its per-environment YAML files exist at:

```
config-server/.../config-repo/discovery-server/
├── discovery-server.yaml          # default profile
├── discovery-server-local.yaml    # local profile
└── discovery-server-Docker.yaml   # Docker profile
```

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

**Evidence source:** `config-repo/application.yaml:107-119`.

Key points:
- **Lease renewal** every 5 seconds; eviction after 15 seconds of missed heartbeats.
- **`prefer-ip-address: true`** — useful in Docker environments where hostnames may not resolve.
- **Random instance ID** appended to allow multiple instances of the same service.

---

## Key Notes

- The discovery-server is **one of the first services to start**, alongside config-server.
- All services (except config-server) register with Eureka on startup.
- Feign clients use the registered service names (e.g., `user-service`, `event-service`) for inter-service HTTP calls.
