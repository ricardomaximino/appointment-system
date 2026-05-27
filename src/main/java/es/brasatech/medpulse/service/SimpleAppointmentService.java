package es.brasatech.medpulse.service;

import es.brasatech.medpulse.domain.*;
import es.brasatech.medpulse.entity.*;
import es.brasatech.medpulse.mapper.*;
import es.brasatech.medpulse.repository.*;
import es.brasatech.medpulse.service.delegation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SimpleAppointmentService implements AppointmentService {

    public Map<String, Doctor> doctors;
    public Map<String, Patient> patients;
    public Map<AppointmentSlot, Patient> slots;
    public Set<LocalDate> companyClosedDates;
    protected AppointmentSlotAvailabilityValidator validator;
    protected AppointmentRepository appointmentRepository;
    protected DoctorMapper doctorMapper;

    public SimpleAppointmentService() {
        var context = DbContext.getContext();
        var doctorRepository = context.getBean(DoctorRepository.class);
        var patientRepository = context.getBean(PatientRepository.class);
        this.appointmentRepository = context.getBean(AppointmentRepository.class);
        var companyClosedDateRepository = context.getBean(CompanyClosedDateRepository.class);

        this.doctorMapper = context.getBean(DoctorMapper.class);
        var patientMapper = context.getBean(PatientMapper.class);
        var appointmentMapper = context.getBean(AppointmentMapper.class);

        this.doctors = new DoctorDelegationMap(doctorRepository, this.doctorMapper);
        this.patients = new PatientDelegationMap(patientRepository, patientMapper);
        this.slots = new SlotDelegationMap(this.appointmentRepository, doctorRepository, patientRepository, this.doctorMapper, patientMapper);
        this.companyClosedDates = new ClosedDateDelegationSet(companyClosedDateRepository);
        this.validator = new AppointmentSlotAvailabilityValidator(this.companyClosedDates);
    }

    public void clearBookings() {
        slots.clear();
    }

    public AppointmentSlot createAppointmentSlot(LocalDateTime appointmentDateTime, String doctorId, AppointmentType appointmentType) {
        var doctor = doctors.get(doctorId);
        if (doctor == null) {
            throw new IllegalArgumentException("Doctor not found: " + doctorId);
        }
        if (!doctor.getAppointmentTypes().contains(appointmentType)) {
            System.out.printf("Doctor %s does not have permissions to be assigned for appointment type %s\n", doctor.getName(), appointmentType);
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

        LocalDateTime startNew = slot.getDateTime();
        LocalDateTime endNew = startNew.plus(slot.getType().getDuration());

        // Optimized: pre-fetch booked appointments for this doctor once
        List<AppointmentEntity> doctorAppointments = appointmentRepository.findByDoctorDoctorId(slot.getDoctor().getDoctorId());

        // Check if doctor has any overlapping appointment slot
        for (var booked : doctorAppointments) {
            LocalDateTime startBooked = booked.getDateTime();
            LocalDateTime endBooked = startBooked.plus(booked.getType().getDuration());

            // Overlap condition: startNew < endBooked AND startBooked < endNew
            if (startNew.isBefore(endBooked) && startBooked.isBefore(endNew)) {
                if (!"true".equals(System.getProperty("benchmark.active"))) {
                    System.out.printf("Overlap detected! Doctor %s is already booked from %s to %s. Desired slot is %s to %s.%n",
                            slot.getDoctor().getName(), startBooked, endBooked, startNew, endNew);
                }
                return false;
            }
        }

        // Artificial tiny delay to simulate core business logic validation processing
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        slots.put(slot, patient);
        if (!"true".equals(System.getProperty("benchmark.active"))) {
            System.out.println("Appointment slot [doctor: %s, start: %s, end: %s ] was registered successfully!".formatted(slot.getDoctor().getName(), startNew, endNew));
        }
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
        if (doctor.getSpecificDatesAvailability() != null && doctor.getSpecificDatesAvailability().containsKey(date)) {
            shifts = doctor.getSpecificDatesAvailability().get(date);
        } else {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (doctor.getWeeklyAvailability() != null && doctor.getWeeklyAvailability().containsKey(dayOfWeek)) {
                shifts = doctor.getWeeklyAvailability().get(dayOfWeek);
            }
        }

        if (shifts == null || shifts.isEmpty()) {
            return List.of();
        }

        // Optimized: pre-fetch booked appointments for this doctor once
        List<AppointmentEntity> doctorAppointments = appointmentRepository.findByDoctorDoctorId(doctorId);

        List<LocalDateTime> availableSlots = new ArrayList<>();

        // Resolve step size based on the doctor's allowed appointment types with the smallest duration value
        long stepMinutes = doctor.getAppointmentTypes().stream()
                .mapToLong(t -> t.getDuration().toMinutes())
                .min()
                .orElse(30); // fallback to 30 minutes if none configured

        // 3. For each active shift, generate potential starting times at dynamically resolved intervals
        for (TimeRange shift : shifts) {
            LocalTime time = shift.start();
            while (time.isBefore(shift.end())) {
                LocalDateTime candidateStart = date.atTime(time);

                // Check overlap against our pre-fetched doctorAppointments list
                boolean isBooked = false;
                for (var booked : doctorAppointments) {
                    LocalDateTime startBooked = booked.getDateTime();
                    LocalDateTime endBooked = startBooked.plus(booked.getType().getDuration());

                    if (!candidateStart.isBefore(startBooked) && candidateStart.isBefore(endBooked)) {
                        isBooked = true;
                        break;
                    }
                }

                if (!isBooked) {
                    availableSlots.add(candidateStart);
                }

                // Advance by step size
                time = time.plusMinutes(stepMinutes);
            }
        }

        if (!"true".equals(System.getProperty("benchmark.active"))) {
            Set<AppointmentSlot> bookedSlots = doctorAppointments.stream()
                    .map(app -> new AppointmentSlot(this.doctorMapper.toDomain(app.getDoctor()), app.getDateTime(), app.getType()))
                    .collect(Collectors.toSet());
            CalendarConsolePrinter.printDayCalendar(doctor, date, availableSlots, bookedSlots);
        }
        return availableSlots;
    }

    @Override
    public List<LocalDateTime> getAvailableSlotsForWeek(String doctorId, LocalDate date) {
        LocalDate startOfWeek = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<LocalDateTime> weeklySlots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            weeklySlots.addAll(getAvailableSlots(doctorId, startOfWeek.plusDays(i)));
        }
        if (!"true".equals(System.getProperty("benchmark.active"))) {
            CalendarConsolePrinter.printWeekCalendar(doctors.get(doctorId), date, weeklySlots);
        }
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
        if (!"true".equals(System.getProperty("benchmark.active"))) {
            CalendarConsolePrinter.printMonthCalendar(doctors.get(doctorId), year, month, monthlySlots);
        }
        return monthlySlots;
    }

    @Override
    public List<LocalDateTime> getAvailableSlotsForYear(String doctorId, int year) {
        List<LocalDateTime> yearlySlots = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            yearlySlots.addAll(getAvailableSlotsForMonth(doctorId, year, month));
        }
        if (!"true".equals(System.getProperty("benchmark.active"))) {
            CalendarConsolePrinter.printYearCalendar(doctors.get(doctorId), year, yearlySlots);
        }
        return yearlySlots;
    }
}