package es.brasatech.medpulse.service;

import es.brasatech.medpulse.domain.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SimpleAppointmentService implements AppointmentService {

    protected Map<String, Doctor> doctors;
    protected Map<String, Patient> patients;
    protected Map<AppointmentSlot, Patient> slots;
    protected Set<LocalDate> companyClosedDates;
    protected AppointmentSlotAvailabilityValidator validator;

    public SimpleAppointmentService() {
        doctors = InMemoryDataStore.doctors;
        patients = InMemoryDataStore.patients;
        slots = InMemoryDataStore.slots;
        companyClosedDates = InMemoryDataStore.companyClosedDates;
        validator = new AppointmentSlotAvailabilityValidator(companyClosedDates);
    }

    public void clearBookings() {
        slots.clear();
    }

    public AppointmentSlot createAppointmentSlot(LocalDateTime appointmentDateTime, String doctorId, AppointmentType appointmentType) {
        var doctor = doctors.get(doctorId);
        if (doctor == null) {
            throw new IllegalArgumentException("Doctor not found: " + doctorId);
        }
        if (!doctor.appointmentTypes().contains(appointmentType)) {
            System.out.printf("Doctor %s does not have permissions to be assigned for appointment type %s\n", doctor.name(), appointmentType);
        }
        var slot = new AppointmentSlot(doctor, appointmentDateTime, appointmentType);
        validator.validate(slot);
        return slot;
    }

    public boolean registerAppointmentSlot(AppointmentSlot slot, Patient patient) {
        if (slot == null || patient == null) {
            return false;
        }

        // Validate availability rules
        try {
            validator.validate(slot);
        } catch (IllegalStateException e) {
            System.out.println("Registration failed: " + e.getMessage());
            return false;
        }

        LocalDateTime startNew = slot.dateTime();
        LocalDateTime endNew = startNew.plus(slot.type().getDuration());

        // Check if doctor has any overlapping appointment slot (strictly single-threaded check)
        for (var entry : slots.entrySet()) {
            var bookedSlot = entry.getKey();
            if (bookedSlot.doctor().doctorId().equals(slot.doctor().doctorId())) {
                LocalDateTime startBooked = bookedSlot.dateTime();
                LocalDateTime endBooked = startBooked.plus(bookedSlot.type().getDuration());

                // Overlap condition: startNew < endBooked AND startBooked < endNew
                if (startNew.isBefore(endBooked) && startBooked.isBefore(endNew)) {
                    System.out.printf("Overlap detected! Doctor %s is already booked from %s to %s. Desired slot is %s to %s.%n",
                            slot.doctor().name(), startBooked, endBooked, startNew, endNew);
                    return false;
                }
            }
        }

        // Artificial tiny delay to simulate core business logic validation processing
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        slots.put(slot, patient);
        System.out.println("Appointment slot [doctor: %s, start: %s, end: %s ] was registered successfully!".formatted(slot.doctor().name(), startNew, endNew));
        return true;
    }

    @Override
    public List<LocalDateTime> getAvailableSlots(String doctorId, LocalDate date) {
        var doctor = doctors.get(doctorId);
        if (doctor == null) {
            throw new IllegalArgumentException("Doctor not found: " + doctorId);
        }

        // 1. Check if company is closed
        if (companyClosedDates.contains(date)) {
            return List.of();
        }

        // 2. Resolve active shifts for the day (prioritize specific dates over weekly recurring schedules)
        List<TimeRange> shifts = null;
        if (doctor.specificDatesAvailability() != null && doctor.specificDatesAvailability().containsKey(date)) {
            shifts = doctor.specificDatesAvailability().get(date);
        } else {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (doctor.weeklyAvailability() != null && doctor.weeklyAvailability().containsKey(dayOfWeek)) {
                shifts = doctor.weeklyAvailability().get(dayOfWeek);
            }
        }

        if (shifts == null || shifts.isEmpty()) {
            return List.of();
        }

        List<LocalDateTime> availableSlots = new ArrayList<>();

        // Resolve step size based on the doctor's allowed appointment types with the smallest duration value
        long stepMinutes = doctor.appointmentTypes().stream()
                .mapToLong(t -> t.getDuration().toMinutes())
                .min()
                .orElse(30); // fallback to 30 minutes if none configured

        // 3. For each active shift, generate potential starting times at dynamically resolved intervals
        for (TimeRange shift : shifts) {
            LocalTime time = shift.start();
            while (time.isBefore(shift.end())) {
                LocalDateTime candidateStart = date.atTime(time);

                // Check if it overlaps with any already registered slots
                boolean isBooked = false;
                for (var entry : slots.entrySet()) {
                    var bookedSlot = entry.getKey();
                    if (bookedSlot.doctor().doctorId().equals(doctorId)) {
                        LocalDateTime startBooked = bookedSlot.dateTime();
                        LocalDateTime endBooked = startBooked.plus(bookedSlot.type().getDuration());

                        if (!candidateStart.isBefore(startBooked) && candidateStart.isBefore(endBooked)) {
                            isBooked = true;
                            break;
                        }
                    }
                }

                if (!isBooked) {
                    availableSlots.add(candidateStart);
                }

                // Advance by step size
                time = time.plusMinutes(stepMinutes);
            }
        }

        CalendarConsolePrinter.printDayCalendar(doctor, date, availableSlots, slots.keySet());
        return availableSlots;
    }

    @Override
    public List<LocalDateTime> getAvailableSlotsForWeek(String doctorId, LocalDate date) {
        LocalDate startOfWeek = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<LocalDateTime> weeklySlots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            weeklySlots.addAll(getAvailableSlots(doctorId, startOfWeek.plusDays(i)));
        }
        CalendarConsolePrinter.printWeekCalendar(doctors.get(doctorId), date, weeklySlots);
        return weeklySlots;
    }

    @Override
    public List<LocalDateTime> getAvailableSlotsForMonth(String doctorId, int year, int month) {
        LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
        int lengthOfMonth = firstDayOfMonth.lengthOfMonth();
        List<LocalDateTime> monthlySlots = new ArrayList<>();
        for (int day = 1; day <= lengthOfMonth; day++) {
            monthlySlots.addAll(getAvailableSlots(doctorId, LocalDate.of(year, month, day)));
        }
        CalendarConsolePrinter.printMonthCalendar(doctors.get(doctorId), year, month, monthlySlots);
        return monthlySlots;
    }

    @Override
    public List<LocalDateTime> getAvailableSlotsForYear(String doctorId, int year) {
        List<LocalDateTime> yearlySlots = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            yearlySlots.addAll(getAvailableSlotsForMonth(doctorId, year, month));
        }
        CalendarConsolePrinter.printYearCalendar(doctors.get(doctorId), year, yearlySlots);
        return yearlySlots;
    }
}