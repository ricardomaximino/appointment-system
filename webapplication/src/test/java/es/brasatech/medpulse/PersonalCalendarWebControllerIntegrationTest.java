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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
public class PersonalCalendarWebControllerIntegrationTest {

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
    private Calendar docPersonalCal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        clearBookingsUseCase.clearBookings();

        // Create standard doctor user
        doctor = new User("doc1", "Dr. Gregory House", Set.of(Role.DOCTOR));
        createUserUseCase.createUser(doctor);

        // Resolve personal calendar
        docPersonalCal = getOrCreateCalendarUseCase.getOrCreateCalendar(doctor.getId(), CalendarType.PERSONAL);
    }

    @Test
    @WithMockUser(username = "doc1", roles = "DOCTOR")
    void testViewDynamicCalendar() throws Exception {
        mockMvc.perform(get("/dynamic-calendar"))
                .andExpect(status().isOk())
                .andExpect(view().name("dynamic-calendar"))
                .andExpect(model().attributeExists("currentUserId"));
    }

    @Test
    @WithMockUser(username = "doc1", roles = "DOCTOR")
    void testGetDynamicCalendarEventsJson() throws Exception {
        // Create a personal event
        Event personalEvent = new Event();
        personalEvent.setCalendarId(docPersonalCal.getId());
        personalEvent.setTitle("Sabbatical Hour");
        personalEvent.setStartDateTime(LocalDateTime.now().plusDays(1).withHour(15).withMinute(0));
        personalEvent.setEndDateTime(LocalDateTime.now().plusDays(1).withHour(16).withMinute(0));
        personalEvent.setStatus("ACTIVE");
        createEventUseCase.createEvent(personalEvent);

        mockMvc.perform(get("/api/dynamic-calendar/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Sabbatical Hour"))
                .andExpect(jsonPath("$[0].type").value("PERSONAL"));
    }

    @Test
    @WithMockUser(username = "doc1", roles = "DOCTOR")
    void testCreatePersonalEvent() throws Exception {
        mockMvc.perform(post("/personal-events/create").with(csrf())
                        .param("title", "Coffee Break")
                        .param("date", LocalDate.now().plusDays(1).toString())
                        .param("startTime", "10:00")
                        .param("endTime", "10:30")
                        .param("avoidOverride", "true")
                        .param("avoidStepOver", "true")
                        .param("redirectSource", "dynamic"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dynamic-calendar"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @WithMockUser(username = "doc1", roles = "DOCTOR")
    void testDeletePersonalEvent() throws Exception {
        Event personalEvent = new Event();
        personalEvent.setCalendarId(docPersonalCal.getId());
        personalEvent.setTitle("Coffee Break");
        personalEvent.setStartDateTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0));
        personalEvent.setEndDateTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(30));
        personalEvent.setStatus("ACTIVE");
        Event created = createEventUseCase.createEvent(personalEvent);

        mockMvc.perform(post("/personal-events/delete/" + created.getId()).with(csrf())
                        .param("redirectSource", "dynamic"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dynamic-calendar"))
                .andExpect(flash().attributeExists("successMessage"));
    }
}
