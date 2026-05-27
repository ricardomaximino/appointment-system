package es.brasatech.medpulse.service;

import es.brasatech.medpulse.domain.AppointmentSlot;
import es.brasatech.medpulse.domain.AppointmentType;
import es.brasatech.medpulse.domain.Patient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentService {

    void clearBookings();

    AppointmentSlot createAppointmentSlot(LocalDateTime appointmentDateTime, String doctorId, AppointmentType appointmentType);

    boolean registerAppointmentSlot(AppointmentSlot slot, Patient patient);

    List<LocalDateTime> getAvailableSlots(String doctorId, LocalDate date);

    List<LocalDateTime> getAvailableSlotsForWeek(String doctorId, LocalDate date);

    List<LocalDateTime> getAvailableSlotsForMonth(String doctorId, int year, int month);

    List<LocalDateTime> getAvailableSlotsForYear(String doctorId, int year);
}

