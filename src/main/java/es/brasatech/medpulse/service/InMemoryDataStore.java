package es.brasatech.medpulse.service;

import es.brasatech.medpulse.domain.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

public class InMemoryDataStore {

    protected static final Map<String, Doctor> doctors = new ConcurrentHashMap<>();
    protected static final Map<String, Patient> patients = new ConcurrentHashMap<>();
    protected static final Map<AppointmentSlot, Patient> slots = new ConcurrentHashMap<>();
    protected static final Set<LocalDate> companyClosedDates = ConcurrentHashMap.newKeySet();
    protected static final Map<String, StampedLock> doctorLocks = new ConcurrentHashMap<>();

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

    public static StampedLock getLockForDoctor(String doctorId) {
        return doctorLocks.computeIfAbsent(doctorId, id -> new StampedLock());
    }
}
