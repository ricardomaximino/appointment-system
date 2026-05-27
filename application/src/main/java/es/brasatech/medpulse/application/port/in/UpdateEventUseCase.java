package es.brasatech.medpulse.application.port.in;

import es.brasatech.medpulse.domain.Event;

public interface UpdateEventUseCase {
    Event updateEvent(Event event);
}
