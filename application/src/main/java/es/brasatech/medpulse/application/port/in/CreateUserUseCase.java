package es.brasatech.medpulse.application.port.in;

import es.brasatech.medpulse.domain.User;

public interface CreateUserUseCase {
    User createUser(User user);
}
