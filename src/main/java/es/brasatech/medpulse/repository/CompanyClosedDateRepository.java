package es.brasatech.medpulse.repository;

import es.brasatech.medpulse.entity.CompanyClosedDateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface CompanyClosedDateRepository extends JpaRepository<CompanyClosedDateEntity, LocalDate> {
}
