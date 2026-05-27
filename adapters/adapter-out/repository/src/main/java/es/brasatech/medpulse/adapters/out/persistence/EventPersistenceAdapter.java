package es.brasatech.medpulse.adapters.out.persistence;

import es.brasatech.medpulse.application.port.out.EventPersistencePort;
import es.brasatech.medpulse.adapters.out.persistence.entity.EventEntity;
import es.brasatech.medpulse.adapters.out.persistence.entity.CalendarEntity;
import es.brasatech.medpulse.adapters.out.persistence.repository.EventEntityRepository;
import es.brasatech.medpulse.adapters.out.persistence.repository.CalendarEntityRepository;
import es.brasatech.medpulse.adapters.out.persistence.mapper.DomainMapper;
import es.brasatech.medpulse.domain.Event;
import es.brasatech.medpulse.domain.CalendarType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EventPersistenceAdapter implements EventPersistencePort {

    private final EventEntityRepository eventEntityRepository;
    private final CalendarEntityRepository calendarEntityRepository;
    private final DomainMapper mapper;

    @Override
    public Event save(Event event) {
        EventEntity entity = mapper.toEntity(event);
        EventEntity saved = eventEntityRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public void deleteById(Long id) {
        eventEntityRepository.deleteById(id);
    }

    @Override
    public Optional<Event> findById(Long id) {
        return eventEntityRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Event> findAll() {
        return eventEntityRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Event> findByCalendarId(String calendarId) {
        if (calendarId == null) {
            return List.of();
        }
        return eventEntityRepository.findByCalendarId(calendarId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Event> findByUserId(String userId) {
        List<CalendarEntity> userCalendars = calendarEntityRepository.findByUserId(userId);
        List<EventEntity> userEvents = new ArrayList<>();
        
        for (CalendarEntity cal : userCalendars) {
            userEvents.addAll(eventEntityRepository.findByCalendarId(cal.getId()));
        }
        
        // Also include events booked by this user (patient bookings)
        List<EventEntity> patientBookings = eventEntityRepository.findByBookedByUserId(userId);
        for (EventEntity pb : patientBookings) {
            if (!userEvents.contains(pb)) {
                userEvents.add(pb);
            }
        }

        return userEvents.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Event> findBookedEventsForDoctor(String doctorId) {
        return calendarEntityRepository.findByUserIdAndType(doctorId, CalendarType.PROFESSIONAL)
                .map(cal -> eventEntityRepository.findByCalendarId(cal.getId()).stream()
                        .map(mapper::toDomain)
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    @Override
    public void deleteByBookedByUserId(String patientId) {
        eventEntityRepository.deleteByBookedByUserId(patientId);
    }

    @Override
    public void deleteByCalendarId(String calendarId) {
        eventEntityRepository.deleteByCalendarId(calendarId);
    }

    @Override
    public void clearAll() {
        eventEntityRepository.deleteAll();
    }
}
