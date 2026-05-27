package es.brasatech.medpulse.adapters.out.persistence.repository;

import es.brasatech.medpulse.adapters.out.persistence.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventEntityRepository extends JpaRepository<EventEntity, Long> {
    List<EventEntity> findByCalendarId(String calendarId);
    List<EventEntity> findByBookedByUserId(String bookedByUserId);
    void deleteByBookedByUserId(String bookedByUserId);
    void deleteByCalendarId(String calendarId);
}
