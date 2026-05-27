package es.brasatech.medpulse.repository;

import es.brasatech.medpulse.entity.AppointmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<AppointmentEntity, Long> {

    Optional<AppointmentEntity> findByDoctorDoctorIdAndDateTime(String doctorId, LocalDateTime dateTime);

    boolean existsByDoctorDoctorIdAndDateTime(String doctorId, LocalDateTime dateTime);

    List<AppointmentEntity> findByDoctorDoctorId(String doctorId);
}
