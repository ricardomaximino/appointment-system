package es.brasatech.medpulse.application.port.out;

import es.brasatech.medpulse.domain.Role;
import java.util.Set;

public interface SecurityContextPort {
    String getCurrentUserId();
    Set<Role> getCurrentUserRoles();
    boolean hasRole(Role role);
}
