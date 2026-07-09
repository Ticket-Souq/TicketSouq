package org.ticketsouq.apigateway.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class RoutesConfig {

    @Value("${EurekaOrigin}")
    private String eurekaOrigin;

    @Bean
    public RouterFunction<ServerResponse> eurekaRoutes() {
        return GatewayRouterFunctions.route("discovery-service")
            .route(RequestPredicates.path("/eureka"), HandlerFunctions.http())
            .before(BeforeFilterFunctions.rewritePath("/eureka", "/"))
            .before(BeforeFilterFunctions.uri(eurekaOrigin))
            .build()
            .and(
                GatewayRouterFunctions.route("discovery-service-static")
                    .route(RequestPredicates.path("/eureka/**"), HandlerFunctions.http())
                    .before(BeforeFilterFunctions.uri(eurekaOrigin))
                    .build()
            );
    }

    @Bean
    public RouterFunction<ServerResponse> serviceRoutes() {
        String[] services = {
            "user-service",
            "analytics-service",
            "audit-service",
            "availability-locking-service",
            "event-service",
            "notification-service",
            "payment-service",
            "reservation-service",
            "ticket-service",
            "venue-service"
        };

        RouterFunction<ServerResponse> routes = null;

        for (String service : services) {
            String prefix = service.replace("-service", "");

            RouterFunction<ServerResponse> serviceRoute =
                GatewayRouterFunctions.route(service)
                    .route(RequestPredicates.path("/api/v1/" + prefix + "/**"), HandlerFunctions.http())
                    .filter(LoadBalancerFilterFunctions.lb(service))  // ← resolves lb:// correctly
                    .build();

            RouterFunction<ServerResponse> docsRoute =
                GatewayRouterFunctions.route(service + "-api-docs")
                    .route(RequestPredicates.path("/aggregate/" + service + "/v3/api-docs"), HandlerFunctions.http())
                    .before(BeforeFilterFunctions.rewritePath(
                        "/aggregate/" + service + "/v3/api-docs",
                        "/v3/api-docs"))
                    .filter(LoadBalancerFilterFunctions.lb(service))  // ← resolves lb:// correctly
                    .build();

            routes = (routes == null)
                ? serviceRoute.and(docsRoute)
                : routes.and(serviceRoute).and(docsRoute);
        }

        return routes;
    }
}
