package es.brasatech.medpulse.application.port.in;

import es.brasatech.medpulse.domain.Event;

public interface CreateEventUseCase {
    Event createEvent(Event event);
}
