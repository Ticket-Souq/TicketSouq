package org.ticketsouq.apigateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.ticketsouq.apigateway.config.Filters.HeaderForwardingFilter;
import org.ticketsouq.apigateway.config.Filters.JwtAuthenticationFilter;
import org.ticketsouq.apigateway.config.Filters.RateLimit.RateLimitFilter;
import org.ticketsouq.apigateway.config.Filters.SecurityHeadersFilter;
import org.ticketsouq.apigateway.model.AuthCredential;
import org.ticketsouq.apigateway.repository.AuthCredentialRepository;

import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final RateLimitFilter rateLimitFilter;
    private final SecurityHeadersFilter securityHeadersFilter;
    private final HeaderForwardingFilter headerForwardingFilter;
    private final AuthCredentialRepository authCredentialRepository;

    // ── Security filter chain ──────────────────────────────────────────────
    // Defines which requests are public, which are internal-only, and which
    // custom filters run before Spring Security's standard auth filter.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            // Allow frontend origins (Angular 4200, React 3000) to call the API
            .cors(Customizer.withDefaults())

            // Stateless API — no CSRF tokens needed
            .csrf(AbstractHttpConfigurer::disable)

            // No HTTP sessions — every request carries a JWT
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Lookup user by ID from the DB when Spring Security needs full details
            .userDetailsService(userDetailsService())

            // ── URL access rules ───────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth

                // Public: service-discovery, docs, monitoring
                .requestMatchers(
                    "/eureka/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/actuator/**",
                    "/aggregate/*/v3/api-docs"
                ).permitAll()

                // Public: registration, login, password reset, email verification
                .requestMatchers("/api/v1/auth/**").permitAll()

                // Internal only: service-to-service calls, blocked from external clients
                .requestMatchers("/api/v1/private/**").denyAll()

                .requestMatchers("/api/v1/user/org/generate-accounts").hasRole("ORG_HEAD")
                // TODO add the rest of the urls 

                // Everything else is reachable for now; method-level @PreAuthorize
                // guards endpoints that need specific roles.
                .anyRequest().permitAll()
            )

            // ── Custom filter order (all run before UsernamePasswordAuthenticationFilter) ──
            // 1. Security headers: set response headers as early as possible
            // 2. Rate limit: throttle before processing expensive auth
            // 3. JWT auth: populate SecurityContext if a valid Bearer token is present
            .addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(headerForwardingFilter, JwtAuthenticationFilter.class)
            .build();
    }

    // Hashes passwords with BCrypt, strength 12 (≈250ms per hash on modern hardware)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // Exposes the Spring Security AuthenticationManager so AuthController
    // can programmatically authenticate login requests if needed.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // Allows http://localhost:4200 (Angular) and http://localhost:3000 (React)
    // to access the API with credentials (cookies, auth headers).
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:4200", "http://localhost:3000","http://localhost:5173"));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // Looks up an AuthCredential by user ID and wraps it in a CustomUserDetails.
    // Assumes the principal identifier passed by the auth provider is a UUID string.
    @Bean
    public UserDetailsService userDetailsService() {
        return id -> {
            AuthCredential credential = authCredentialRepository.findByUserId(UUID.fromString(id))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
            return new CustomUserDetails(credential);
        };
    }

}
