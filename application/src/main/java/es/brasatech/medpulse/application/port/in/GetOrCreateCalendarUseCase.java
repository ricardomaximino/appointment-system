package es.brasatech.medpulse.application.port.in;

import es.brasatech.medpulse.domain.Calendar;
import es.brasatech.medpulse.domain.CalendarType;

public interface GetOrCreateCalendarUseCase {
    Calendar getOrCreateCalendar(String userId, CalendarType type);
}
