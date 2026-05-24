package es.brasatech.medpulse.service;

import es.brasatech.medpulse.domain.AppointmentSlot;
import es.brasatech.medpulse.domain.AppointmentType;
import es.brasatech.medpulse.domain.Patient;

import java.time.LocalDateTime;

public interface AppointmentService {

    void clearBookings();

    AppointmentSlot createSimpleAppointmentSlot(LocalDateTime appointmentDateTime, String doctorId, AppointmentType appointmentType);

    boolean registerSimpleAppointmentSlot(AppointmentSlot slot, Patient patient);
}
