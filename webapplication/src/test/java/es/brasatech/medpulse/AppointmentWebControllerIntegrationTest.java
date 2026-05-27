package es.brasatech.medpulse;

import es.brasatech.medpulse.application.port.in.*;
import es.brasatech.medpulse.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
public class AppointmentWebControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

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
    private Event pendingEvent;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        clearBookingsUseCase.clearBookings();

        // Create standard mock doctor user
        doctor = new User("doc1", "Dr. Gregory House", Set.of(Role.DOCTOR));
        doctor.setWeeklyAvailability(Map.of(
                DayOfWeek.MONDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0))),
                DayOfWeek.TUESDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0))),
                DayOfWeek.WEDNESDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0))),
                DayOfWeek.THURSDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0))),
                DayOfWeek.FRIDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0))),
                DayOfWeek.SATURDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0))),
                DayOfWeek.SUNDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0)))
        ));
        doctor.setAppointmentTypes(List.of(AppointmentType.SHORT));
        createUserUseCase.createUser(doctor);

        // Create standard mock patient user
        patient = new User("pat1", "John Doe", Set.of(Role.PATIENT));
        createUserUseCase.createUser(patient);

        // Resolve doctor calendars
        docPersonalCal = getOrCreateCalendarUseCase.getOrCreateCalendar(doctor.getId(), CalendarType.PERSONAL);
        docProfCal = getOrCreateCalendarUseCase.getOrCreateCalendar(doctor.getId(), CalendarType.PROFESSIONAL);

        // Seed a pending appointment for pat1
        Event event = new Event();
        event.setCalendarId(docProfCal.getId());
        event.setTitle("Medical Appointment: SHORT");
        event.setStartDateTime(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0));
        event.setEndDateTime(LocalDateTime.now().plusDays(2).withHour(10).withMinute(15));
        event.setType(AppointmentType.SHORT);
        event.setBookedByUserId(patient.getId());
        event.setStatus("PENDING_ACCEPTANCE");
        pendingEvent = createEventUseCase.createEvent(event);
    }

    @Test
    @WithMockUser(username = "pat1", roles = "PATIENT")
    void testPatientAccessDashboard_AndCalendarViews() throws Exception {
        // Access Home Dashboard
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("currentUserId"))
                .andExpect(model().attributeExists("pendingAppointments"));

        // Access Appointments listing
        mockMvc.perform(get("/appointments"))
                .andExpect(status().isOk())
                .andExpect(view().name("appointments"))
                .andExpect(model().attributeExists("appointments"));

        // Access Merged Calendar View
        mockMvc.perform(get("/calendar").param("viewMode", "merged"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar"))
                .andExpect(model().attribute("viewMode", "merged"))
                .andExpect(model().attributeExists("mergedEvents"));

        // Access Split Calendar View
        mockMvc.perform(get("/calendar").param("viewMode", "split"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar"))
                .andExpect(model().attribute("viewMode", "split"))
                .andExpect(model().attributeExists("personalEvents"))
                .andExpect(model().attributeExists("professionalEvents"));
    }

    @Test
    @WithMockUser(username = "pat1", roles = "PATIENT")
    void testPatientAccept_AndDeclineAppointment() throws Exception {
        // 1. Accept pending invitation
        mockMvc.perform(post("/appointments/accept/" + pendingEvent.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/appointments"))
                .andExpect(flash().attributeExists("successMessage"));

        // 2. Seed a new pending appointment to test decline auditing notes
        Event anotherEvent = new Event();
        anotherEvent.setCalendarId(docProfCal.getId());
        anotherEvent.setTitle("Medical Appointment: SHORT");
        anotherEvent.setStartDateTime(LocalDateTime.now().plusDays(3).withHour(11).withMinute(0));
        anotherEvent.setEndDateTime(LocalDateTime.now().plusDays(3).withHour(11).withMinute(15));
        anotherEvent.setType(AppointmentType.SHORT);
        anotherEvent.setBookedByUserId(patient.getId());
        anotherEvent.setStatus("PENDING_ACCEPTANCE");
        Event anotherPending = createEventUseCase.createEvent(anotherEvent);

        // 3. Decline the appointment
        mockMvc.perform(post("/appointments/decline/" + anotherPending.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/appointments"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminAccessAllowedResources() throws Exception {
        // Admin should successfully access all admin-only dashboard resources
        mockMvc.perform(get("/doctors"))
                .andExpect(status().isOk())
                .andExpect(view().name("doctors"));

        mockMvc.perform(get("/patients"))
                .andExpect(status().isOk())
                .andExpect(view().name("patients"));

        mockMvc.perform(get("/closed-dates"))
                .andExpect(status().isOk())
                .andExpect(view().name("closed-dates"));
    }

    @Test
    @WithMockUser(username = "pat1", roles = "PATIENT")
    void testPatientRestrictedFromAdminResources() throws Exception {
        // Standard patient should be completely forbidden from accessing admin pages
        mockMvc.perform(get("/doctors"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/patients"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/closed-dates"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "doc1", roles = "DOCTOR")
    void testDoctorCreatePersonalEvent() throws Exception {
        mockMvc.perform(post("/personal-events/create").with(csrf())
                        .param("title", "Lunch Break")
                        .param("date", LocalDate.now().plusDays(2).toString())
                        .param("startTime", "12:00")
                        .param("endTime", "13:00")
                        .param("avoidOverride", "true")
                        .param("avoidStepOver", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @WithMockUser(username = "doc1", roles = "DOCTOR")
    void testDoctorDeletePersonalEvent() throws Exception {
        // Create an event manually in DB to delete
        Event personalEvent = new Event();
        personalEvent.setCalendarId(docPersonalCal.getId());
        personalEvent.setTitle("Lunch Break");
        personalEvent.setStartDateTime(LocalDateTime.now().plusDays(2).withHour(12).withMinute(0));
        personalEvent.setEndDateTime(LocalDateTime.now().plusDays(2).withHour(13).withMinute(0));
        personalEvent.setType(null);
        personalEvent.setBookedByUserId(null);
        personalEvent.setStatus("ACTIVE");
        Event createdEvent = createEventUseCase.createEvent(personalEvent);

        mockMvc.perform(post("/personal-events/delete/" + createdEvent.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"))
                .andExpect(flash().attributeExists("successMessage"));
    }
}
