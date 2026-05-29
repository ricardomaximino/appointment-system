package es.brasatech.medpulse.adapters.in.web;

import es.brasatech.medpulse.application.port.in.*;
import es.brasatech.medpulse.application.port.out.SecurityContextPort;
import es.brasatech.medpulse.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class PersonalCalendarWebController {

    private final CreateEventUseCase createEventUseCase;
    private final DeleteEventUseCase deleteEventUseCase;
    private final GetCalendarUseCase getCalendarUseCase;
    private final GetOrCreateCalendarUseCase getOrCreateCalendarUseCase;
    private final GetUserUseCase getUserUseCase;
    private final GetEventsForUserUseCase getEventsForUserUseCase;
    private final SecurityContextPort securityContextPort;

    public record CalendarEventCompat(
            Long id,
            String title,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String type,
            String status,
            String statusNotes,
            String doctorName,
            String patientName,
            boolean avoidOverride,
            boolean avoidStepOver
    ) {}

    @GetMapping("/dynamic-calendar")
    public String viewDynamicCalendar(Model model) {
        String currentUserId = securityContextPort.getCurrentUserId();
        User currentUser = getUserUseCase.getUser(currentUserId);
        
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isUserAdmin", securityContextPort.hasRole(Role.ADMIN));
        
        return "dynamic-calendar";
    }

    @GetMapping("/api/dynamic-calendar/events")
    @ResponseBody
    public List<CalendarEventCompat> getDynamicCalendarEvents() {
        String currentUserId = securityContextPort.getCurrentUserId();
        if (currentUserId == null) {
            return new ArrayList<>();
        }

        List<Event> myEvents = getEventsForUserUseCase.getEventsForUser(currentUserId);
        return myEvents.stream().map(event -> {
            Calendar cal = getCalendarUseCase.getCalendar(event.getCalendarId());
            boolean isPersonal = cal != null && CalendarType.PERSONAL.equals(cal.getType());
            
            String doctorName = "Unassigned";
            if (cal != null) {
                User doc = getUserUseCase.getUser(cal.getUserId());
                if (doc != null && doc.hasRole(Role.DOCTOR)) {
                    doctorName = doc.getName();
                }
            }
            String patientName = "N/A";
            if (event.getBookedByUserId() != null) {
                User pat = getUserUseCase.getUser(event.getBookedByUserId());
                if (pat != null) {
                    patientName = pat.getName();
                }
            }

            return new CalendarEventCompat(
                    event.getId(),
                    event.getTitle(),
                    event.getStartDateTime(),
                    event.getEndDateTime(),
                    isPersonal ? "PERSONAL" : "PROFESSIONAL",
                    event.getStatus(),
                    event.getStatusNotes(),
                    doctorName,
                    patientName,
                    event.isAvoidOverride(),
                    event.isAvoidStepOver()
            );
        }).collect(Collectors.toList());
    }

    @PostMapping("/personal-events/create")
    public String createPersonalEvent(@RequestParam String title,
                                      @RequestParam String date,
                                      @RequestParam String startTime,
                                      @RequestParam String endTime,
                                      @RequestParam(required = false, defaultValue = "false") boolean avoidOverride,
                                      @RequestParam(required = false, defaultValue = "false") boolean avoidStepOver,
                                      @RequestParam(required = false, defaultValue = "dynamic") String redirectSource,
                                      RedirectAttributes redirectAttributes) {
        try {
            String currentUserId = securityContextPort.getCurrentUserId();
            if (currentUserId == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Error: User is not authenticated.");
                return "redirect:/login";
            }

            LocalDate localDate = LocalDate.parse(date);
            LocalTime startLocalTime = LocalTime.parse(startTime);
            LocalTime endLocalTime = LocalTime.parse(endTime);

            if (startLocalTime.isAfter(endLocalTime)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Error: Start time must be before end time.");
                return "redirect:" + ("calendar".equals(redirectSource) ? "/calendar" : "/dynamic-calendar");
            }

            LocalDateTime startDateTime = LocalDateTime.of(localDate, startLocalTime);
            LocalDateTime endDateTime = LocalDateTime.of(localDate, endLocalTime);

            Calendar personalCalendar = getOrCreateCalendarUseCase.getOrCreateCalendar(currentUserId, CalendarType.PERSONAL);

            Event event = new Event();
            event.setCalendarId(personalCalendar.getId());
            event.setTitle(title);
            event.setStartDateTime(startDateTime);
            event.setEndDateTime(endDateTime);
            event.setType(null);
            event.setBookedByUserId(null);
            event.setAvoidOverride(avoidOverride);
            event.setAvoidStepOver(avoidStepOver);
            event.setStatus("ACTIVE");

            createEventUseCase.createEvent(event);
            redirectAttributes.addFlashAttribute("successMessage", "Personal event successfully scheduled!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to schedule personal event: " + e.getMessage());
        }
        return "redirect:" + ("calendar".equals(redirectSource) ? "/calendar" : "/dynamic-calendar");
    }

    @PostMapping("/personal-events/delete/{id}")
    public String deletePersonalEvent(@PathVariable Long id,
                                      @RequestParam(required = false, defaultValue = "dynamic") String redirectSource,
                                      RedirectAttributes redirectAttributes) {
        try {
            deleteEventUseCase.deleteEvent(id);
            redirectAttributes.addFlashAttribute("successMessage", "Personal event deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete personal event: " + e.getMessage());
        }
        return "redirect:" + ("calendar".equals(redirectSource) ? "/calendar" : "/dynamic-calendar");
    }
}
