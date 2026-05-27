package es.brasatech.medpulse.adapters.out.persistence;

import es.brasatech.medpulse.application.port.out.CalendarPersistencePort;
import es.brasatech.medpulse.adapters.out.persistence.entity.CalendarEntity;
import es.brasatech.medpulse.adapters.out.persistence.repository.CalendarEntityRepository;
import es.brasatech.medpulse.adapters.out.persistence.mapper.DomainMapper;
import es.brasatech.medpulse.domain.Calendar;
import es.brasatech.medpulse.domain.CalendarType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CalendarPersistenceAdapter implements CalendarPersistencePort {

    private final CalendarEntityRepository calendarEntityRepository;
    private final DomainMapper mapper;

    @Override
    public Calendar save(Calendar calendar) {
        CalendarEntity entity = mapper.toEntity(calendar);
        CalendarEntity saved = calendarEntityRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Calendar> findById(String id) {
        return calendarEntityRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Calendar> findByUserId(String userId) {
        return calendarEntityRepository.findByUserId(userId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Calendar> findByUserIdAndType(String userId, CalendarType type) {
        return calendarEntityRepository.findByUserIdAndType(userId, type).map(mapper::toDomain);
    }
}
