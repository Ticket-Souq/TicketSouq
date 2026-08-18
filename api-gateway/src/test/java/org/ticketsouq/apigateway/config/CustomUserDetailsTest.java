package org.ticketsouq.apigateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.ticketsouq.apigateway.model.AuthCredential;
import org.ticketsouq.apigateway.model.Role;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomUserDetailsTest {

    private AuthCredential customerCredential;
    private AuthCredential verifiedCredential;

    @BeforeEach
    void setUp() {
        customerCredential = AuthCredential.builder()
                .userId(UUID.randomUUID())
                .email("customer@test.com")
                .passwordHash("hash")
                .role(Role.CUSTOMER)
                .isActive(true)
                .isVerified(false)
                .locked(false)
                .build();

        verifiedCredential = AuthCredential.builder()
                .userId(UUID.randomUUID())
                .email("verified@test.com")
                .passwordHash("hash")
                .role(Role.CUSTOMER)
                .isActive(true)
                .isVerified(true)
                .locked(false)
                .build();
    }

    @Test
    void shouldIncludeRoleAndEmailVerifiedAuthorities() {
        CustomUserDetails details = new CustomUserDetails(verifiedCredential);

        Collection<? extends GrantedAuthority> authorities = details.getAuthorities();

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "ROLE_CUSTOMER",
                        "CUSTOMER",
                        "EMAIL_VERIFIED");
    }

    @Test
    void shouldNotIncludeEmailVerifiedWhenNotVerified() {
        CustomUserDetails details = new CustomUserDetails(customerCredential);

        Collection<? extends GrantedAuthority> authorities = details.getAuthorities();

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_CUSTOMER", "CUSTOMER");
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .doesNotContain("EMAIL_VERIFIED");
    }

    @Test
    void shouldCacheAuthorities() {
        CustomUserDetails details = new CustomUserDetails(verifiedCredential);

        Collection<? extends GrantedAuthority> first = details.getAuthorities();
        Collection<? extends GrantedAuthority> second = details.getAuthorities();

        assertThat(first).isSameAs(second);
    }

}
