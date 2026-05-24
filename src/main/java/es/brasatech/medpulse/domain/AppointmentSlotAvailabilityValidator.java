package es.brasatech.medpulse.domain;

import lombok.RequiredArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public class AppointmentSlotAvailabilityValidator {

    private final Set<LocalDate> companyClosedDates;

    public void validate(AppointmentSlot slot) {
        if (slot == null) {
            throw new IllegalArgumentException("Slot cannot be null");
        }
        Doctor doctor = slot.doctor();
        LocalDateTime startDateTime = slot.dateTime();
        LocalDate appointmentDate = startDateTime.toLocalDate();
        LocalTime startTime = startDateTime.toLocalTime();
        LocalTime endTime = startTime.plus(slot.type().getDuration());

        // 1. Check if the company is closed
        if (companyClosedDates.contains(appointmentDate)) {
            throw new IllegalStateException("Appointment cannot be scheduled: Company is closed on " + appointmentDate);
        }

        // 2. Resolve active shifts for the day (prioritize specific dates over weekly recurring schedules)
        List<TimeRange> shifts = null;
        if (doctor.specificDatesAvailability() != null && doctor.specificDatesAvailability().containsKey(appointmentDate)) {
            shifts = doctor.specificDatesAvailability().get(appointmentDate);
        } else {
            DayOfWeek dayOfWeek = startDateTime.getDayOfWeek();
            if (doctor.weeklyAvailability() != null && doctor.weeklyAvailability().containsKey(dayOfWeek)) {
                shifts = doctor.weeklyAvailability().get(dayOfWeek);
            }
        }

        if (shifts == null || shifts.isEmpty()) {
            throw new IllegalStateException("Appointment cannot be scheduled: Doctor %s does not work on %s".formatted(doctor.name(), appointmentDate));
        }

        // 3. Check doctor's working shifts (must fall fully within at least one working shift)
        boolean fitsInShift = false;
        for (TimeRange shift : shifts) {
            if (shift.contains(startTime, endTime)) {
                fitsInShift = true;
                break;
            }
        }

        if (!fitsInShift) {
            throw new IllegalStateException("Appointment cannot be scheduled: Desired time %s - %s is outside Doctor %s's working shifts for %s".formatted(
                    startTime, endTime, doctor.name(), appointmentDate));
        }
    }
}
