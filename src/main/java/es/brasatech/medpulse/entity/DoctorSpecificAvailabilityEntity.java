package es.brasatech.medpulse.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "doctor_specific_availabilities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoctorSpecificAvailabilityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private DoctorEntity doctor;

    private LocalDate date;

    private LocalTime startTime;
    private LocalTime endTime;
}
