package es.brasatech.medpulse.application.port.out;

import es.brasatech.medpulse.domain.Event;

import java.util.List;
import java.util.Optional;

public interface EventPersistencePort {
    Event save(Event event);
    void deleteById(Long id);
    Optional<Event> findById(Long id);
    List<Event> findAll();
    List<Event> findByCalendarId(String calendarId);
    List<Event> findByUserId(String userId);
    List<Event> findBookedEventsForDoctor(String doctorId);
    
    // Cascades helper operations
    void deleteByBookedByUserId(String patientId);
    void deleteByCalendarId(String calendarId);
    void clearAll();
}
