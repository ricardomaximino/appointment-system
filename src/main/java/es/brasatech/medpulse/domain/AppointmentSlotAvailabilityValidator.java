package es.brasatech.medpulse.domain;

import es.brasatech.medpulse.service.DomainDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AppointmentSlotAvailabilityValidator {

    private final DomainDataService domainDataService;

    public void validate(AppointmentSlot slot) {
        if (slot == null) {
            throw new IllegalArgumentException("Slot cannot be null");
        }
        Doctor doctor = slot.getDoctor();
        LocalDateTime startDateTime = slot.getDateTime();
        LocalDate appointmentDate = startDateTime.toLocalDate();
        LocalTime startTime = startDateTime.toLocalTime();
        LocalTime endTime = startTime.plus(slot.getType().getDuration());

        // 1. Check if the company is closed
        if (domainDataService.isCompanyClosed(appointmentDate)) {
            throw new IllegalStateException("Appointment cannot be scheduled: Company is closed on " + appointmentDate);
        }

        // 2. Resolve active shifts for the day (prioritize specific dates over weekly recurring schedules)
        List<TimeRange> shifts = null;
        if (doctor.getSpecificDatesAvailability() != null && doctor.getSpecificDatesAvailability().containsKey(appointmentDate)) {
            shifts = doctor.getSpecificDatesAvailability().get(appointmentDate);
        } else {
            DayOfWeek dayOfWeek = startDateTime.getDayOfWeek();
            if (doctor.getWeeklyAvailability() != null && doctor.getWeeklyAvailability().containsKey(dayOfWeek)) {
                shifts = doctor.getWeeklyAvailability().get(dayOfWeek);
            }
        }

        if (shifts == null || shifts.isEmpty()) {
            throw new IllegalStateException("Appointment cannot be scheduled: Doctor %s does not work on %s".formatted(doctor.getName(), appointmentDate));
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
                    startTime, endTime, doctor.getName(), appointmentDate));
        }
    }
}
