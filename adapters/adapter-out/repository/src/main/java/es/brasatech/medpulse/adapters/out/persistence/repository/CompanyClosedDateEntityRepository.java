package es.brasatech.medpulse.adapters.out.persistence.repository;

import es.brasatech.medpulse.adapters.out.persistence.entity.CompanyClosedDateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface CompanyClosedDateEntityRepository extends JpaRepository<CompanyClosedDateEntity, LocalDate> {
}
