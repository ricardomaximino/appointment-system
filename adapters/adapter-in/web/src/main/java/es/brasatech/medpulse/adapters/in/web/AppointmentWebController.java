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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class AppointmentWebController {

    private final CreateEventUseCase createEventUseCase;
    private final UpdateEventUseCase updateEventUseCase;
    private final DeleteEventUseCase deleteEventUseCase;
    private final CancelEventUseCase cancelEventUseCase;
    private final GetEventUseCase getEventUseCase;
    private final GetAllEventsUseCase getAllEventsUseCase;
    private final GetAvailableSlotsUseCase getAvailableSlotsUseCase;
    private final CheckEventConflictUseCase checkEventConflictUseCase;

    private final CreateUserUseCase createUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;
    private final GetUserUseCase getUserUseCase;
    private final GetUsersByRoleUseCase getUsersByRoleUseCase;

    private final GetCalendarUseCase getCalendarUseCase;
    private final GetOrCreateCalendarUseCase getOrCreateCalendarUseCase;

    private final AddClosedDateUseCase addClosedDateUseCase;
    private final RemoveClosedDateUseCase removeClosedDateUseCase;
    private final GetAllClosedDatesUseCase getAllClosedDatesUseCase;

    private final SecurityContextPort securityContextPort;
    private final GetEventsForUserUseCase getEventsForUserUseCase;

    // --- Thymeleaf View Compatibility Wrappers ---

    public record DoctorCompat(String doctorId, String name) {}
    public record PatientCompat(String patientId, String name) {}

    public static class AppointmentCompat {
        private final Long id;
        private final DoctorCompat doctor;
        private final LocalDateTime dateTime;
        private final AppointmentType type;
        private final PatientCompat patient;
        private final String status;
        private final String statusNotes;

        public AppointmentCompat(Long id, DoctorCompat doctor, LocalDateTime dateTime, AppointmentType type, PatientCompat patient, String status, String statusNotes) {
            this.id = id;
            this.doctor = doctor;
            this.dateTime = dateTime;
            this.type = type;
            this.patient = patient;
            this.status = status;
            this.statusNotes = statusNotes;
        }

        public Long getId() { return id; }
        public DoctorCompat getDoctor() { return doctor; }
        public LocalDateTime getDateTime() { return dateTime; }
        public AppointmentType getType() { return type; }
        public PatientCompat getPatient() { return patient; }
        public String getStatus() { return status; }
        public String getStatusNotes() { return statusNotes; }
    }

    public record CalendarEventCompat(
            Long id,
            String title,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String type,
            String status,
            String statusNotes,
            String doctorName,
            String patientName
    ) {}

    private AppointmentCompat mapToCompat(Event event) {
        if (event == null) return null;

        // Resolve doctor compat from the event's calendar owner
        DoctorCompat docCompat = null;
        if (event.getCalendarId() != null) {
            Calendar calendar = getCalendarUseCase.getCalendar(event.getCalendarId());
            if (calendar != null) {
                User doctorUser = getUserUseCase.getUser(calendar.getUserId());
                if (doctorUser != null) {
                    docCompat = new DoctorCompat(doctorUser.getId(), doctorUser.getName());
                }
            }
        }

        // Resolve patient compat from the booked patient ID
        PatientCompat patCompat = null;
        if (event.getBookedByUserId() != null) {
            User patientUser = getUserUseCase.getUser(event.getBookedByUserId());
            if (patientUser != null) {
                patCompat = new PatientCompat(patientUser.getId(), patientUser.getName());
            }
        }

        return new AppointmentCompat(
                event.getId(),
                docCompat,
                event.getStartDateTime(),
                event.getType(),
                patCompat,
                event.getStatus(),
                event.getStatusNotes()
        );
    }

    // --- Endpoints ---

    @GetMapping("/")
    public String home(Model model) {
        String currentUserId = securityContextPort.getCurrentUserId();
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("isUserAdmin", securityContextPort.hasRole(Role.ADMIN));
        
        model.addAttribute("doctorCount", getUsersByRoleUseCase.getUsersByRole(Role.DOCTOR).size());
        model.addAttribute("patientCount", getUsersByRoleUseCase.getUsersByRole(Role.PATIENT).size());
        
        List<Event> allEvents = getAllEventsUseCase.getAllEvents();
        model.addAttribute("appointmentCount", allEvents.stream().filter(e -> "ACTIVE".equalsIgnoreCase(e.getStatus())).count());
        
        List<AppointmentCompat> rebookAppointments = allEvents.stream()
                .filter(e -> "REBOOK".equalsIgnoreCase(e.getStatus()))
                .map(this::mapToCompat)
                .collect(Collectors.toList());
        model.addAttribute("rebookAppointments", rebookAppointments);
        
        // Notifications and pending requests for patients
        long pendingCount = 0;
        List<AppointmentCompat> pendingAppointments = new ArrayList<>();
        if (currentUserId != null) {
            pendingAppointments = allEvents.stream()
                    .filter(e -> "PENDING_ACCEPTANCE".equalsIgnoreCase(e.getStatus()) && currentUserId.equals(e.getBookedByUserId()))
                    .map(this::mapToCompat)
                    .collect(Collectors.toList());
            pendingCount = pendingAppointments.size();
        }
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("pendingAppointments", pendingAppointments);
        
        return "index";
    }

    @GetMapping("/book")
    public String bookingForm(@RequestParam(required = false) Long rebookAppointmentId, Model model) {
        String currentUserId = securityContextPort.getCurrentUserId();
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("isUserAdmin", securityContextPort.hasRole(Role.ADMIN));

        model.addAttribute("doctors", getUsersByRoleUseCase.getUsersByRole(Role.DOCTOR));
        model.addAttribute("patients", getUsersByRoleUseCase.getUsersByRole(Role.PATIENT));
        model.addAttribute("appointmentTypes", AppointmentType.values());
        
        List<String> companyClosedDates = getAllClosedDatesUseCase.getAllClosedDates().stream()
                .map(LocalDate::toString)
                .collect(Collectors.toList());
        model.addAttribute("companyClosedDates", companyClosedDates);
        
        if (rebookAppointmentId != null) {
            Event rebookEvent = getEventUseCase.getEvent(rebookAppointmentId);
            if (rebookEvent != null) {
                model.addAttribute("rebookAppointmentId", rebookAppointmentId);
                model.addAttribute("preselectedPatientId", rebookEvent.getBookedByUserId());
                model.addAttribute("preselectedType", rebookEvent.getType());
                if (rebookEvent.getStartDateTime() != null) {
                    model.addAttribute("preselectedDate", rebookEvent.getStartDateTime().toLocalDate().toString());
                }
            }
        }
        
        return "book";
    }

    @PostMapping("/book")
    public String submitBooking(@RequestParam String doctorId,
                                @RequestParam String date,
                                @RequestParam String time,
                                @RequestParam String patientId,
                                @RequestParam AppointmentType appointmentType,
                                @RequestParam(required = false) Long rebookAppointmentId,
                                @RequestParam(required = false, defaultValue = "false") boolean avoidOverride,
                                @RequestParam(required = false, defaultValue = "false") boolean avoidStepOver,
                                RedirectAttributes redirectAttributes) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            LocalTime localTime = LocalTime.parse(time);
            LocalDateTime dateTime = LocalDateTime.of(localDate, localTime);

            Calendar profCalendar = getOrCreateCalendarUseCase.getOrCreateCalendar(doctorId, CalendarType.PROFESSIONAL);

            // Create Event representing the scheduled booking
            Event event = new Event();
            event.setCalendarId(profCalendar.getId());
            event.setTitle("Medical Appointment: " + appointmentType);
            event.setStartDateTime(dateTime);
            event.setEndDateTime(dateTime.plus(appointmentType.getDuration()));
            event.setType(appointmentType);
            event.setBookedByUserId(patientId);
            event.setAvoidOverride(avoidOverride);
            event.setAvoidStepOver(avoidStepOver);
            
            // Professional bookings default to PENDING_ACCEPTANCE so patient must accept it!
            event.setStatus("PENDING_ACCEPTANCE");

            Event saved = createEventUseCase.createEvent(event);

            if (saved != null) {
                if (rebookAppointmentId != null) {
                    deleteEventUseCase.deleteEvent(rebookAppointmentId);
                }
                redirectAttributes.addFlashAttribute("successMessage", "Appointment request created! Pending patient acceptance.");
                return "redirect:/appointments";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Failed to book appointment. Overlapping booking conflict detected.");
                return "redirect:/book" + (rebookAppointmentId != null ? "?rebookAppointmentId=" + rebookAppointmentId : "");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error booking appointment: " + e.getMessage());
            return "redirect:/book" + (rebookAppointmentId != null ? "?rebookAppointmentId=" + rebookAppointmentId : "");
        }
    }

    @PostMapping("/appointments/accept/{id}")
    public String acceptAppointment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Event event = getEventUseCase.getEvent(id);
            if (event == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Appointment request not found.");
                return "redirect:/";
            }

            // Verify logged-in patient matches
            String currentUserId = securityContextPort.getCurrentUserId();
            if (!event.getBookedByUserId().equals(currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized: You are not this patient.");
                return "redirect:/";
            }

            // Update status and verify conflicts under standard rules
            event.setStatus("ACTIVE");
            if (checkEventConflictUseCase.hasConflict(event)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Cannot accept appointment! Overlapping conflict detected on your calendar.");
                return "redirect:/";
            }

            updateEventUseCase.updateEvent(event);
            redirectAttributes.addFlashAttribute("successMessage", "Appointment accepted and added to your calendar!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to accept appointment: " + e.getMessage());
        }
        return "redirect:/appointments";
    }

    @PostMapping("/appointments/decline/{id}")
    public String declineAppointment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Event event = getEventUseCase.getEvent(id);
            if (event == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Appointment request not found.");
                return "redirect:/";
            }

            // Verify logged-in patient matches
            String currentUserId = securityContextPort.getCurrentUserId();
            if (!event.getBookedByUserId().equals(currentUserId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized: You are not this patient.");
                return "redirect:/";
            }

            // Decline audit note formatting: "Declined by the patient at dd/MM/yyyy HH:mm"
            String formattedTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            event.setStatus("CANCEL");
            event.setStatusNotes("Declined by the patient at " + formattedTime);

            updateEventUseCase.updateEvent(event);
            redirectAttributes.addFlashAttribute("successMessage", "Appointment declined successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to decline appointment: " + e.getMessage());
        }
        return "redirect:/appointments";
    }

    @GetMapping("/appointments")
    public String listAppointments(Model model) {
        String currentUserId = securityContextPort.getCurrentUserId();
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("isUserAdmin", securityContextPort.hasRole(Role.ADMIN));

        List<AppointmentCompat> compats = getAllEventsUseCase.getAllEvents().stream()
                .filter(e -> {
                    if (e.getCalendarId() != null) {
                        Calendar cal = getCalendarUseCase.getCalendar(e.getCalendarId());
                        if (cal != null && CalendarType.PERSONAL.equals(cal.getType())) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(this::mapToCompat)
                .collect(Collectors.toList());
        model.addAttribute("appointments", compats);
        return "appointments";
    }

    @PostMapping("/appointments/cancel/{id}")
    public String cancelAppointment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            cancelEventUseCase.cancelEvent(id);
            redirectAttributes.addFlashAttribute("successMessage", "Appointment cancelled successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to cancel appointment: " + e.getMessage());
        }
        return "redirect:/appointments";
    }

    @GetMapping("/doctors")
    public String listDoctors(Model model) {
        model.addAttribute("doctors", getUsersByRoleUseCase.getUsersByRole(Role.DOCTOR));
        model.addAttribute("appointmentTypes", AppointmentType.values());
        model.addAttribute("doctor", new User("", "", Set.of(Role.DOCTOR)));
        return "doctors";
    }

    @PostMapping("/doctors/save")
    public String saveDoctor(@RequestParam String doctorId,
                             @RequestParam String name,
                             @RequestParam(required = false) List<AppointmentType> appointmentTypes,
                             RedirectAttributes redirectAttributes) {
        try {
            if (appointmentTypes == null) {
                appointmentTypes = new ArrayList<>();
            }
            User doctor = getUserUseCase.getUser(doctorId);
            if (doctor == null) {
                doctor = new User(doctorId, name, Set.of(Role.DOCTOR));
            } else {
                doctor.setName(name);
            }
            doctor.setAppointmentTypes(appointmentTypes);
            createUserUseCase.createUser(doctor);
            redirectAttributes.addFlashAttribute("successMessage", "Doctor saved successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to save doctor: " + e.getMessage());
        }
        return "redirect:/doctors";
    }

    @PostMapping("/doctors/delete/{id}")
    public String deleteDoctor(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            deleteUserUseCase.deleteUser(id);
            redirectAttributes.addFlashAttribute("successMessage", "Doctor deleted successfully. Active appointments flagged for rebooking.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete doctor: " + e.getMessage());
        }
        return "redirect:/doctors";
    }

    @GetMapping("/patients")
    public String listPatients(Model model) {
        model.addAttribute("patients", getUsersByRoleUseCase.getUsersByRole(Role.PATIENT));
        model.addAttribute("patient", new User("", "", Set.of(Role.PATIENT)));
        return "patients";
    }

    @PostMapping("/patients/save")
    public String savePatient(@RequestParam String patientId,
                              @RequestParam String name,
                              RedirectAttributes redirectAttributes) {
        try {
            User patient = getUserUseCase.getUser(patientId);
            if (patient == null) {
                patient = new User(patientId, name, Set.of(Role.PATIENT));
            } else {
                patient.setName(name);
            }
            createUserUseCase.createUser(patient);
            redirectAttributes.addFlashAttribute("successMessage", "Patient saved successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to save patient: " + e.getMessage());
        }
        return "redirect:/patients";
    }

    @PostMapping("/patients/delete/{id}")
    public String deletePatient(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            deleteUserUseCase.deleteUser(id);
            redirectAttributes.addFlashAttribute("successMessage", "Patient deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete patient: " + e.getMessage());
        }
        return "redirect:/patients";
    }

    @GetMapping("/closed-dates")
    public String listClosedDates(Model model) {
        model.addAttribute("closedDates", getAllClosedDatesUseCase.getAllClosedDates());
        return "closed-dates";
    }

    @PostMapping("/closed-dates/add")
    public String addClosedDate(@RequestParam String date, RedirectAttributes redirectAttributes) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            addClosedDateUseCase.addClosedDate(localDate);
            redirectAttributes.addFlashAttribute("successMessage", "Company closed date added successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to add closed date: " + e.getMessage());
        }
        return "redirect:/closed-dates";
    }

    @PostMapping("/closed-dates/delete")
    public String deleteClosedDate(@RequestParam String date, RedirectAttributes redirectAttributes) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            removeClosedDateUseCase.removeClosedDate(localDate);
            redirectAttributes.addFlashAttribute("successMessage", "Company closed date removed successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to remove closed date: " + e.getMessage());
        }
        return "redirect:/closed-dates";
    }

    @GetMapping("/calendar")
    public String viewCalendar(@RequestParam(required = false, defaultValue = "merged") String viewMode,
                               @RequestParam(required = false) String viewDoctorId,
                               Model model) {
        String currentUserId = securityContextPort.getCurrentUserId();
        User currentUser = getUserUseCase.getUser(currentUserId);
        
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isUserAdmin", securityContextPort.hasRole(Role.ADMIN));
        model.addAttribute("viewMode", viewMode);

        // Fetch logged-in user's events
        List<Event> myEvents = getEventsForUserUseCase.getEventsForUser(currentUserId);
        
        List<CalendarEventCompat> personalEvents = new ArrayList<>();
        List<CalendarEventCompat> professionalEvents = new ArrayList<>();
        List<CalendarEventCompat> mergedEvents = new ArrayList<>();

        for (Event event : myEvents) {
            Calendar cal = getCalendarUseCase.getCalendar(event.getCalendarId());
            boolean isPersonal = cal != null && CalendarType.PERSONAL.equals(cal.getType());
            
            // Resolve doctor and patient names
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

            CalendarEventCompat compat = new CalendarEventCompat(
                    event.getId(),
                    event.getTitle(),
                    event.getStartDateTime(),
                    event.getEndDateTime(),
                    isPersonal ? "PERSONAL" : "PROFESSIONAL",
                    event.getStatus(),
                    event.getStatusNotes(),
                    doctorName,
                    patientName
            );

            if (isPersonal) {
                personalEvents.add(compat);
            } else {
                professionalEvents.add(compat);
            }
            mergedEvents.add(compat);
        }

        model.addAttribute("personalEvents", personalEvents);
        model.addAttribute("professionalEvents", professionalEvents);
        model.addAttribute("mergedEvents", mergedEvents);

        // If the user is admin, fetch doctor lists and support doctor view tab
        if (securityContextPort.hasRole(Role.ADMIN)) {
            List<User> doctors = getUsersByRoleUseCase.getUsersByRole(Role.DOCTOR);
            model.addAttribute("doctors", doctors);

            if (viewDoctorId != null && !viewDoctorId.isEmpty()) {
                User selectedDoc = getUserUseCase.getUser(viewDoctorId);
                model.addAttribute("selectedDoctor", selectedDoc);

                List<Event> docEvents = getEventsForUserUseCase.getEventsForUser(viewDoctorId);
                List<CalendarEventCompat> docCompatEvents = docEvents.stream()
                        .map(e -> {
                            Calendar cal = getCalendarUseCase.getCalendar(e.getCalendarId());
                            boolean isPersonal = cal != null && CalendarType.PERSONAL.equals(cal.getType());
                            
                            String doctorName = selectedDoc != null ? selectedDoc.getName() : "Unassigned";
                            String patientName = "N/A";
                            if (e.getBookedByUserId() != null) {
                                User pat = getUserUseCase.getUser(e.getBookedByUserId());
                                if (pat != null) {
                                    patientName = pat.getName();
                                }
                            }
                            return new CalendarEventCompat(
                                    e.getId(),
                                    e.getTitle(),
                                    e.getStartDateTime(),
                                    e.getEndDateTime(),
                                    isPersonal ? "PERSONAL" : "PROFESSIONAL",
                                    e.getStatus(),
                                    e.getStatusNotes(),
                                    doctorName,
                                    patientName
                            );
                        })
                        .collect(Collectors.toList());
                model.addAttribute("selectedDoctorEvents", docCompatEvents);
            }
        }

        return "calendar";
    }


    // --- JSON REST API Endpoints ---

    @GetMapping("/api/slots")
    @ResponseBody
    public List<String> getSlotsJson(@RequestParam String doctorId, 
                                     @RequestParam String date,
                                     @RequestParam(required = false) AppointmentType type) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            List<LocalDateTime> slots;
            if (type != null) {
                slots = getAvailableSlotsUseCase.getAvailableSlots(doctorId, localDate, type);
            } else {
                slots = getAvailableSlotsUseCase.getAvailableSlots(doctorId, localDate);
            }
            return slots.stream()
                    .map(dt -> dt.toLocalTime().toString())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    public record DoctorInfoResponse(
            String doctorId,
            String name,
            List<String> allowedTypes,
            List<String> weeklyAvailabilityDays,
            List<String> specificAvailabilityDates,
            List<String> specificClosedDates,
            List<String> companyClosedDates
    ) {}

    @GetMapping("/api/doctor-info")
    @ResponseBody
    public DoctorInfoResponse getDoctorInfo(@RequestParam String doctorId) {
        try {
            User doctor = getUserUseCase.getUser(doctorId);
            if (doctor == null || !doctor.hasRole(Role.DOCTOR)) {
                return null;
            }
            
            List<String> allowedTypes = doctor.getAppointmentTypes().stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());

            List<String> weeklyAvailabilityDays = doctor.getWeeklyAvailability().keySet().stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());

            List<String> specificAvailabilityDates = doctor.getSpecificDatesAvailability().entrySet().stream()
                    .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                    .map(e -> e.getKey().toString())
                    .collect(Collectors.toList());

            List<String> specificClosedDates = doctor.getSpecificDatesAvailability().entrySet().stream()
                    .filter(e -> e.getValue() == null || e.getValue().isEmpty())
                    .map(e -> e.getKey().toString())
                    .collect(Collectors.toList());

            List<String> companyClosedDates = getAllClosedDatesUseCase.getAllClosedDates().stream()
                    .map(LocalDate::toString)
                    .collect(Collectors.toList());

            return new DoctorInfoResponse(
                    doctor.getId(),
                    doctor.getName(),
                    allowedTypes,
                    weeklyAvailabilityDays,
                    specificAvailabilityDates,
                    specificClosedDates,
                    companyClosedDates
            );
        } catch (Exception e) {
            return null;
        }
    }
}
