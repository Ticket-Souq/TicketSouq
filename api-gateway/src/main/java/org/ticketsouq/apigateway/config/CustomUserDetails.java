package org.ticketsouq.apigateway.config;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ticketsouq.apigateway.model.AuthCredential;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@EqualsAndHashCode
public class CustomUserDetails implements UserDetails {

    @Getter
    private final AuthCredential user;

    // Cached authority list so Spring Security doesn't rebuild it on every permission check
    private List<GrantedAuthority> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (authorities != null) return authorities;
        authorities = new ArrayList<>();
        // Keep both formats so hasRole("ADMIN") and hasAuthority("ROLE_ADMIN") both work
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        authorities.add(new SimpleGrantedAuthority(user.getRole().name()));
        if (user.getIsVerified()) authorities.add(new SimpleGrantedAuthority("EMAIL_VERIFIED"));
        return authorities;
    }

    public UUID getUserId()                   { return user.getUserId(); }
    @Override public String getPassword()     { return user.getPasswordHash(); }
    @Override public String getUsername()     { return user.getEmail(); }
    @Override public boolean isAccountNonLocked()   { return !user.getLocked(); }
    @Override public boolean isEnabled()            { return user.getIsVerified(); }
    @Override public boolean isAccountNonExpired()  { return user.getIsActive(); }
    @Override public boolean isCredentialsNonExpired() { return user.getIsActive(); }
}
