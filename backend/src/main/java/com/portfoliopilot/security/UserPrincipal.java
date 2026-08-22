package com.portfoliopilot.security;

import com.portfoliopilot.model.User;
import com.portfoliopilot.model.enums.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated principal placed in the {@code SecurityContext}.
 *
 * <p>Holds the caller's {@code userId}, which every ownership check reads. It is
 * derived from the verified JWT and is the ONLY accepted source of identity -
 * a {@code userId} in a request body is always ignored.
 *
 * <p>Carries no password: the filter authenticates by signature, not by
 * credentials, so there is nothing to compare and nothing worth leaking.
 */
public record UserPrincipal(
        String userId,
        String email,
        String username,
        Role role
) implements UserDetails {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole());
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        // Spring Security's "username" is the principal identifier. The stable
        // user id is used rather than the display handle, so a username change
        // never invalidates an in-flight request.
        return userId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
