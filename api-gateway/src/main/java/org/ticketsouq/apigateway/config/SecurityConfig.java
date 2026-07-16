package org.ticketsouq.apigateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
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
import org.ticketsouq.apigateway.config.Filters.RateLimit.RateLimitProperties;
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
    private final RateLimitProperties rateLimitProperties;
    private final List<SecurityRule> securityRules;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Lookup user by ID from the DB when Spring Security needs full details
            .userDetailsService(userDetailsService())

            // ── URL access rules (defined in SecurityRulesConfig) ──────────
            .authorizeHttpRequests(auth -> {
                for (var rule : securityRules) {
                    var matcher = rule.method() != null
                        ? auth.requestMatchers(rule.method(), rule.patterns().toArray(String[]::new))
                        : auth.requestMatchers(rule.patterns().toArray(String[]::new));
                    switch (rule.access()) {
                        case PERMIT_ALL -> matcher.permitAll();
                        case DENY_ALL -> matcher.denyAll();
                        case HAS_ROLE -> matcher.hasRole(rule.roles().getFirst());
                        case HAS_ANY_ROLE -> matcher.hasAnyRole(rule.roles().toArray(String[]::new));
                        case AUTHENTICATED -> matcher.authenticated();
                    }
                }
                auth.anyRequest().permitAll();
            })

            .addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(headerForwardingFilter, JwtAuthenticationFilter.class)
            .build();
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(rateLimitProperties.getAllowedOrigins());
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return id -> {
            AuthCredential credential = authCredentialRepository.findByUserId(UUID.fromString(id))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
            return new CustomUserDetails(credential);
        };
    }

}
