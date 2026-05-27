package es.brasatech.medpulse.application.port.in;

import es.brasatech.medpulse.domain.User;
import es.brasatech.medpulse.domain.Role;
import java.util.List;

public interface GetUsersByRoleUseCase {
    List<User> getUsersByRole(Role role);
}
