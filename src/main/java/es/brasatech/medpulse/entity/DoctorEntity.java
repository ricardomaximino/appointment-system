package es.brasatech.medpulse.entity;

import es.brasatech.medpulse.domain.AppointmentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "doctors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoctorEntity {

    @Id
    private String doctorId;
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "doctor_appointment_types", joinColumns = @JoinColumn(name = "doctor_id"))
    @Column(name = "appointment_type")
    @Enumerated(EnumType.STRING)
    private List<AppointmentType> appointmentTypes = new ArrayList<>();

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<DoctorWeeklyAvailabilityEntity> weeklyAvailabilityList = new ArrayList<>();

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<DoctorSpecificAvailabilityEntity> specificDatesAvailabilityList = new ArrayList<>();
}
