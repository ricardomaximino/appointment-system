package es.brasatech.medpulse.service;

import es.brasatech.medpulse.domain.*;

import java.time.LocalDate;
import java.util.List;

public interface DomainDataService {
    void clearBookings();
    Doctor findDoctorById(String doctorId);
    Patient findPatientById(String patientId);
    boolean isCompanyClosed(LocalDate date);
    void addCompanyClosedDate(LocalDate date);
    void removeCompanyClosedDate(LocalDate date);
    List<Appointment> findBookedAppointmentsForDoctor(String doctorId);
    void saveAppointment(AppointmentSlot slot, Patient patient);
    long getAppointmentCount();
}
