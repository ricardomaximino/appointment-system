package es.brasatech.medpulse.application.service;

import es.brasatech.medpulse.application.port.in.*;
import es.brasatech.medpulse.application.port.out.CalendarPersistencePort;
import es.brasatech.medpulse.domain.Calendar;
import es.brasatech.medpulse.domain.CalendarType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarServiceImpl implements CreateCalendarUseCase, GetCalendarUseCase, GetCalendarsByUserUseCase, GetOrCreateCalendarUseCase {

    private final CalendarPersistencePort calendarPersistencePort;

    @Override
    @Transactional
    public Calendar createCalendar(Calendar calendar) {
        return calendarPersistencePort.save(calendar);
    }

    @Override
    public Calendar getCalendar(String id) {
        return calendarPersistencePort.findById(id).orElse(null);
    }

    @Override
    public List<Calendar> getCalendarsByUser(String userId) {
        return calendarPersistencePort.findByUserId(userId);
    }

    @Override
    @Transactional
    public Calendar getOrCreateCalendar(String userId, CalendarType type) {
        return calendarPersistencePort.findByUserIdAndType(userId, type)
                .orElseGet(() -> {
                    String name = "%s's %s Calendar".formatted(userId, type.name());
                    String calId = "%s_%s".formatted(userId, type.name().toLowerCase());
                    Calendar newCal = new Calendar(calId, name, type, userId);
                    return calendarPersistencePort.save(newCal);
                });
    }
}
