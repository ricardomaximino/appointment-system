package es.brasatech.medpulse.controller;

import es.brasatech.medpulse.domain.*;
import es.brasatech.medpulse.service.AppointmentService;
import es.brasatech.medpulse.service.DomainDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class AppointmentWebController {

    @Autowired
    @Qualifier("simpleAppointmentServiceImpl")
    private AppointmentService appointmentService;

    @Autowired
    private DomainDataService domainDataService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("doctorCount", domainDataService.findAllDoctors().size());
        model.addAttribute("patientCount", domainDataService.findAllPatients().size());
        model.addAttribute("appointmentCount", domainDataService.getAppointmentCount());
        return "index";
    }

    @GetMapping("/book")
    public String bookingForm(Model model) {
        model.addAttribute("doctors", domainDataService.findAllDoctors());
        model.addAttribute("patients", domainDataService.findAllPatients());
        model.addAttribute("appointmentTypes", AppointmentType.values());
        return "book";
    }

    @PostMapping("/book")
    public String submitBooking(@RequestParam String doctorId,
                                @RequestParam String date,
                                @RequestParam String time,
                                @RequestParam String patientId,
                                @RequestParam AppointmentType appointmentType,
                                RedirectAttributes redirectAttributes) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            LocalTime localTime = LocalTime.parse(time);
            LocalDateTime dateTime = LocalDateTime.of(localDate, localTime);

            Patient patient = domainDataService.findPatientById(patientId);
            AppointmentSlot slot = appointmentService.createAppointmentSlot(dateTime, doctorId, appointmentType);
            boolean success = appointmentService.registerAppointmentSlot(slot, patient);

            if (success) {
                redirectAttributes.addFlashAttribute("successMessage", "Appointment booked successfully!");
                return "redirect:/appointments";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Failed to book appointment. The slot is already booked or overlapping.");
                return "redirect:/book";
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error booking appointment: " + e.getMessage());
            return "redirect:/book";
        }
    }

    @GetMapping("/appointments")
    public String listAppointments(Model model) {
        model.addAttribute("appointments", domainDataService.findAllBookedAppointments());
        return "appointments";
    }

    @PostMapping("/appointments/cancel/{id}")
    public String cancelAppointment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            domainDataService.deleteAppointment(id);
            redirectAttributes.addFlashAttribute("successMessage", "Appointment cancelled successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to cancel appointment: " + e.getMessage());
        }
        return "redirect:/appointments";
    }

    @GetMapping("/closed-dates")
    public String listClosedDates(Model model) {
        model.addAttribute("closedDates", domainDataService.findAllCompanyClosedDates());
        return "closed-dates";
    }

    @PostMapping("/closed-dates/add")
    public String addClosedDate(@RequestParam String date, RedirectAttributes redirectAttributes) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            domainDataService.addCompanyClosedDate(localDate);
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
            domainDataService.removeCompanyClosedDate(localDate);
            redirectAttributes.addFlashAttribute("successMessage", "Company closed date removed successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to remove closed date: " + e.getMessage());
        }
        return "redirect:/closed-dates";
    }

    @GetMapping("/api/slots")
    @ResponseBody
    public List<String> getSlotsJson(@RequestParam String doctorId, @RequestParam String date) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            List<LocalDateTime> slots = appointmentService.getAvailableSlots(doctorId, localDate);
            return slots.stream()
                    .map(dt -> dt.toLocalTime().toString())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }
}
