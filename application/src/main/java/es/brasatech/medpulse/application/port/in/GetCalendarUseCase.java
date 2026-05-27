package es.brasatech.medpulse.application.port.in;

import es.brasatech.medpulse.domain.Calendar;

public interface GetCalendarUseCase {
    Calendar getCalendar(String id);
}
