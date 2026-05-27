package es.brasatech.medpulse;

import es.brasatech.medpulse.application.port.in.*;
import es.brasatech.medpulse.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ConflictOverrideIntegrationTest {

    @Autowired
    private CreateUserUseCase createUserUseCase;

    @Autowired
    private GetOrCreateCalendarUseCase getOrCreateCalendarUseCase;

    @Autowired
    private CreateEventUseCase createEventUseCase;

    @Autowired
    private ClearBookingsUseCase clearBookingsUseCase;

    private User doctor;
    private User patient;
    private Calendar docPersonalCal;
    private Calendar docProfCal;

    @BeforeEach
    void setUp() {
        // Clear all events/bookings first to ensure isolation
        clearBookingsUseCase.clearBookings();

        // Create doctor user
        String docId = "test-doc-" + System.currentTimeMillis();
        doctor = new User(docId, "Test Doctor", Set.of(Role.DOCTOR));
        // Add availability for Dr. Test on Monday
        doctor.setWeeklyAvailability(Map.of(
                DayOfWeek.MONDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0)))
        ));
        doctor.setAppointmentTypes(List.of(AppointmentType.SHORT));
        createUserUseCase.createUser(doctor);

        // Create patient user
        String patId = "test-pat-" + System.currentTimeMillis();
        patient = new User(patId, "Test Patient", Set.of(Role.PATIENT));
        createUserUseCase.createUser(patient);

        // Retrieve or create professional and personal calendars for the doctor
        docPersonalCal = getOrCreateCalendarUseCase.getOrCreateCalendar(docId, CalendarType.PERSONAL);
        docProfCal = getOrCreateCalendarUseCase.getOrCreateCalendar(docId, CalendarType.PROFESSIONAL);
    }

    @Test
    void testConflictOverrideEnabled_ExpectFailure() {
        // 1. Add a personal event to a doctor's personal calendar
        LocalDate nextMonday = getNextMonday();
        LocalDateTime personalStart = nextMonday.atTime(10, 0);
        LocalDateTime personalEnd = nextMonday.atTime(11, 0);

        Event personalEvent = new Event(
                null,
                docPersonalCal.getId(),
                "Doctor Personal Time",
                personalStart,
                personalEnd,
                true, // avoidOverride
                true, // avoidStepOver
                "ACTIVE",
                null,
                null
        );
        createEventUseCase.createEvent(personalEvent);

        // 2. Try to book a professional appointment overlapping that slot with avoidOverride enabled -> expect FAILURE
        LocalDateTime profStart = nextMonday.atTime(10, 30);
        LocalDateTime profEnd = nextMonday.atTime(11, 0);

        Event overlappingProfEvent = new Event(
                null,
                docProfCal.getId(),
                "Overlapping Professional Appt",
                profStart,
                profEnd,
                true, // avoidOverride
                true, // avoidStepOver
                "ACTIVE",
                patient.getId(),
                AppointmentType.SHORT
        );

        assertThrows(IllegalStateException.class, () -> {
            createEventUseCase.createEvent(overlappingProfEvent);
        }, "Should fail to book overlapping appointment when avoidOverride is enabled");
    }

    @Test
    void testConflictOverrideDisabled_ExpectSuccess() {
        // 1. Add a personal event to doctor's personal calendar
        LocalDate nextMonday = getNextMonday();
        LocalDateTime personalStart = nextMonday.atTime(10, 0);
        LocalDateTime personalEnd = nextMonday.atTime(11, 0);

        Event personalEvent = new Event(
                null,
                docPersonalCal.getId(),
                "Doctor Personal Time",
                personalStart,
                personalEnd,
                false, // avoidOverride = false
                false, // avoidStepOver = false
                "ACTIVE",
                null,
                null
        );
        createEventUseCase.createEvent(personalEvent);

        // 2. Try to book a professional appointment overlapping that slot with avoidOverride disabled -> expect SUCCESS
        LocalDateTime profStart = nextMonday.atTime(10, 30);
        LocalDateTime profEnd = nextMonday.atTime(11, 0);

        Event overlappingProfEvent = new Event(
                null,
                docProfCal.getId(),
                "Overlapping Professional Appt",
                profStart,
                profEnd,
                false, // avoidOverride = false
                false, // avoidStepOver = false
                "ACTIVE",
                patient.getId(),
                AppointmentType.SHORT
        );

        Event savedEvent = createEventUseCase.createEvent(overlappingProfEvent);
        assertNotNull(savedEvent);
        assertNotNull(savedEvent.getId());
        assertEquals("Overlapping Professional Appt", savedEvent.getTitle());
    }

    private LocalDate getNextMonday() {
        LocalDate date = LocalDate.now();
        while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            date = date.plusDays(1);
        }
        return date;
    }
}
