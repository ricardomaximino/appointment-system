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
public class AppointmentSlot {

    private Doctor doctor;
    private LocalDateTime dateTime;
    private AppointmentType type;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppointmentSlot that = (AppointmentSlot) o;
        return Objects.equals(doctor != null ? doctor.getDoctorId() : null, that.doctor != null ? that.doctor.getDoctorId() : null) &&
                Objects.equals(dateTime, that.dateTime) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(doctor != null ? doctor.getDoctorId() : null, dateTime, type);
    }
}