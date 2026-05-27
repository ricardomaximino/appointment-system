package es.brasatech.medpulse.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {
    private Long id;
    private Doctor doctor;
    private Patient patient;
    private LocalDateTime dateTime;
    private AppointmentType type;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Appointment that = (Appointment) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(doctor != null ? doctor.getDoctorId() : null, that.doctor != null ? that.doctor.getDoctorId() : null) &&
                Objects.equals(patient != null ? patient.getPatientId() : null, that.patient != null ? that.patient.getPatientId() : null) &&
                Objects.equals(dateTime, that.dateTime) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, doctor != null ? doctor.getDoctorId() : null, patient != null ? patient.getPatientId() : null, dateTime, type);
    }
}
