package es.brasatech.medpulse.service;

import es.brasatech.medpulse.domain.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

public class ConcurrentAppointmentService implements AppointmentService {

    public static final Map<String, Doctor> doctors = new ConcurrentHashMap<>();
    public static final Map<String, Patient> patients = new ConcurrentHashMap<>();
    public static final Map<AppointmentSlot, Patient> slots = new ConcurrentHashMap<>();
    public static final Set<LocalDate> companyClosedDates = ConcurrentHashMap.newKeySet();
    public static final AppointmentSlotAvailabilityValidator validator = new AppointmentSlotAvailabilityValidator(companyClosedDates);
    private static final StampedLock lock = new StampedLock();

    static {
        // Initialize dummy doctors with availability schedules (split shifts / lunch breaks)
        var houseAvailability = Map.of(
                DayOfWeek.MONDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), new TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0))),
                DayOfWeek.WEDNESDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), new TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0))),
                DayOfWeek.FRIDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), new TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0)))
        );
        var doc1 = new Doctor("doc1", "Dr. House", List.of(AppointmentType.SHORT, AppointmentType.MEDIUM), houseAvailability);

        var greyAvailability = Map.of(
                DayOfWeek.TUESDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0)), new TimeRange(LocalTime.of(13, 0), LocalTime.of(16, 0))),
                DayOfWeek.THURSDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0)), new TimeRange(LocalTime.of(13, 0), LocalTime.of(16, 0))),
                DayOfWeek.SATURDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0)))
        );
        var doc2 = new Doctor("doc2", "Dr. Grey", List.of(AppointmentType.MEDIUM, AppointmentType.LONG), greyAvailability);

        // Seed doc3 (Dr. Strange) with specific date availabilities using dynamic relative dates
        var strangeAvailability = Map.of(
                LocalDate.now().plusDays(2), List.of(new TimeRange(LocalTime.of(8, 30), LocalTime.of(12, 0)), new TimeRange(LocalTime.of(17, 0), LocalTime.of(18, 0))),
                LocalDate.now().plusDays(5), List.of(new TimeRange(LocalTime.of(11, 0), LocalTime.of(17, 0)))
        );
        var doc3 = new Doctor("doc3", "Dr. Strange", List.of(AppointmentType.SHORT, AppointmentType.MEDIUM, AppointmentType.LONG), Map.of(), strangeAvailability);

        doctors.put(doc1.doctorId(), doc1);
        doctors.put(doc2.doctorId(), doc2);
        doctors.put(doc3.doctorId(), doc3);

        // Initialize dummy patients
        var pat1 = new Patient("pat1", "John Doe");
        var pat2 = new Patient("pat2", "Jane Smith");
        patients.put(pat1.patientId(), pat1);
        patients.put(pat2.patientId(), pat2);

        // Default company closed date for testing: e.g. 15 days from now
        companyClosedDates.add(LocalDate.now().plusDays(15));
    }

    public void clearBookings() {
        slots.clear();
    }

    public AppointmentSlot createSimpleAppointmentSlot(LocalDateTime appointmentDateTime, String doctorId, AppointmentType appointmentType) {
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

    public boolean registerSimpleAppointmentSlot(AppointmentSlot slot, Patient patient) {
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

        // Lock globally to ensure checking availability and booking happen atomically (Check-Then-Act serialization)
        long stamp = lock.writeLock();
        try {
            // Check if doctor has any overlapping appointment slot
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
        } finally {
            lock.unlockWrite(stamp);
        }
    }
}
