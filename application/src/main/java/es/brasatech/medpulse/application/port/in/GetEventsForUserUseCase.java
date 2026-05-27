package es.brasatech.medpulse.application.port.in;

import es.brasatech.medpulse.domain.Event;
import java.util.List;

public interface GetEventsForUserUseCase {
    List<Event> getEventsForUser(String userId);
}
