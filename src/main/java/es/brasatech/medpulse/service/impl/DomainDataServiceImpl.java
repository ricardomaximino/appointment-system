package es.brasatech.medpulse.service.impl;

import es.brasatech.medpulse.domain.*;
import es.brasatech.medpulse.entity.*;
import es.brasatech.medpulse.mapper.*;
import es.brasatech.medpulse.repository.*;
import es.brasatech.medpulse.service.DomainDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DomainDataServiceImpl implements DomainDataService {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final CompanyClosedDateRepository companyClosedDateRepository;

    private final DoctorMapper doctorMapper;
    private final PatientMapper patientMapper;
    private final AppointmentMapper appointmentMapper;

    @Override
    @Transactional
    public void clearBookings() {
        appointmentRepository.deleteAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Doctor findDoctorById(String doctorId) {
        return doctorRepository.findById(doctorId)
                .map(doctorMapper::toDomain)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Patient findPatientById(String patientId) {
        return patientRepository.findById(patientId)
                .map(patientMapper::toDomain)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCompanyClosed(LocalDate date) {
        return companyClosedDateRepository.existsById(date);
    }

    @Override
    @Transactional
    public void addCompanyClosedDate(LocalDate date) {
        companyClosedDateRepository.save(new CompanyClosedDateEntity(date));
    }

    @Override
    @Transactional
    public void removeCompanyClosedDate(LocalDate date) {
        companyClosedDateRepository.deleteById(date);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Appointment> findBookedAppointmentsForDoctor(String doctorId) {
        return appointmentRepository.findByDoctorDoctorId(doctorId).stream()
                .map(appointmentMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void saveAppointment(AppointmentSlot slot, Patient patient) {
        var docEnt = doctorRepository.findById(slot.getDoctor().getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found: " + slot.getDoctor().getDoctorId()));
        var patEnt = patientRepository.findById(patient.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + patient.getPatientId()));

        var appointment = new AppointmentEntity(null, docEnt, slot.getDateTime(), slot.getType(), patEnt);
        appointmentRepository.save(appointment);
    }

    @Override
    @Transactional(readOnly = true)
    public long getAppointmentCount() {
        return appointmentRepository.count();
    }
}
