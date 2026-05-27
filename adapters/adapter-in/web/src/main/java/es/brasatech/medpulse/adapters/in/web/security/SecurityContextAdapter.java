package es.brasatech.medpulse.adapters.in.web.security;

import es.brasatech.medpulse.application.port.out.SecurityContextPort;
import es.brasatech.medpulse.domain.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SecurityContextAdapter implements SecurityContextPort {

    @Override
    public String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return auth.getName(); // maps directly to User ID
    }

    @Override
    public Set<Role> getCurrentUserRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .filter(name -> {
                    try {
                        Role.valueOf(name);
                        return true;
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .map(Role::valueOf)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean hasRole(Role role) {
        return getCurrentUserRoles().contains(role);
    }
}
