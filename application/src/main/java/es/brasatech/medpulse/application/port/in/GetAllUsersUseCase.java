package es.brasatech.medpulse.application.port.in;

import es.brasatech.medpulse.domain.User;
import java.util.List;

public interface GetAllUsersUseCase {
    List<User> getAllUsers();
}
