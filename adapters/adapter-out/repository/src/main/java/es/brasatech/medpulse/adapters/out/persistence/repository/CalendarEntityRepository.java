package es.brasatech.medpulse.adapters.out.persistence.repository;

import es.brasatech.medpulse.adapters.out.persistence.entity.CalendarEntity;
import es.brasatech.medpulse.domain.CalendarType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CalendarEntityRepository extends JpaRepository<CalendarEntity, String> {
    List<CalendarEntity> findByUserId(String userId);
    Optional<CalendarEntity> findByUserIdAndType(String userId, CalendarType type);
}
