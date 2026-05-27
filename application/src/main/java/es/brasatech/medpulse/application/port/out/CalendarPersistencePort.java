package es.brasatech.medpulse.application.port.out;

import es.brasatech.medpulse.domain.Calendar;
import es.brasatech.medpulse.domain.CalendarType;

import java.util.List;
import java.util.Optional;

public interface CalendarPersistencePort {
    Calendar save(Calendar calendar);
    Optional<Calendar> findById(String id);
    List<Calendar> findByUserId(String userId);
    Optional<Calendar> findByUserIdAndType(String userId, CalendarType type);
}
