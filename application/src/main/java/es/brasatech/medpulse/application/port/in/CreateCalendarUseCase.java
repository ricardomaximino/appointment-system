package es.brasatech.medpulse.application.port.in;

import es.brasatech.medpulse.domain.Calendar;

public interface CreateCalendarUseCase {
    Calendar createCalendar(Calendar calendar);
}
