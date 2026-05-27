package es.brasatech.medpulse.application.port.in;

import es.brasatech.medpulse.domain.User;

public interface UpdateUserUseCase {
    User updateUser(User user);
}
