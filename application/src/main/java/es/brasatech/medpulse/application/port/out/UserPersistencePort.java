package es.brasatech.medpulse.application.port.out;

import es.brasatech.medpulse.domain.User;
import es.brasatech.medpulse.domain.Role;

import java.util.List;
import java.util.Optional;

public interface UserPersistencePort {
    User save(User user);
    void deleteById(String id);
    Optional<User> findById(String id);
    List<User> findAll();
    List<User> findByRole(Role role);
}
