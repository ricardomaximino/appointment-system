package es.brasatech.medpulse.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record Doctor(
        String doctorId,
        String name,
        List<AppointmentType> appointmentTypes,
        Map<DayOfWeek, List<TimeRange>> weeklyAvailability,
        Map<LocalDate, List<TimeRange>> specificDatesAvailability
) {
    public Doctor(String doctorId, String name, List<AppointmentType> appointmentTypes, Map<DayOfWeek, List<TimeRange>> weeklyAvailability) {
        this(doctorId, name, appointmentTypes, weeklyAvailability, Map.of());
    }
}