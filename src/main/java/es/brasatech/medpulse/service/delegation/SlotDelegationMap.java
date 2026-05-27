package es.brasatech.medpulse.service.delegation;

import es.brasatech.medpulse.domain.AppointmentSlot;
import es.brasatech.medpulse.domain.Patient;
import es.brasatech.medpulse.entity.AppointmentEntity;
import es.brasatech.medpulse.mapper.DoctorMapper;
import es.brasatech.medpulse.mapper.PatientMapper;
import es.brasatech.medpulse.repository.AppointmentRepository;
import es.brasatech.medpulse.repository.DoctorRepository;
import es.brasatech.medpulse.repository.PatientRepository;

import java.util.AbstractMap;
import java.util.Set;
import java.util.stream.Collectors;

public class SlotDelegationMap extends AbstractMap<AppointmentSlot, Patient> {
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorMapper doctorMapper;
    private final PatientMapper patientMapper;

    public SlotDelegationMap(AppointmentRepository appointmentRepository,
                             DoctorRepository doctorRepository,
                             PatientRepository patientRepository,
                             DoctorMapper doctorMapper,
                             PatientMapper patientMapper) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.doctorMapper = doctorMapper;
        this.patientMapper = patientMapper;
    }

    @Override
    public Patient get(Object key) {
        if (key instanceof AppointmentSlot slot) {
            return appointmentRepository.findByDoctorDoctorIdAndDateTime(
                    slot.getDoctor().getDoctorId(), slot.getDateTime())
                    .map(AppointmentEntity::getPatient)
                    .map(patientMapper::toDomain)
                    .orElse(null);
        }
        return null;
    }

    @Override
    public Patient put(AppointmentSlot key, Patient value) {
        var docEnt = doctorRepository.findById(key.getDoctor().getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found: " + key.getDoctor().getDoctorId()));
        var patEnt = patientRepository.findById(value.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + value.getPatientId()));

        // Delete existing if any to emulate map override
        appointmentRepository.findByDoctorDoctorIdAndDateTime(docEnt.getDoctorId(), key.getDateTime())
                .ifPresent(appointmentRepository::delete);

        var appointment = new AppointmentEntity(null, docEnt, key.getDateTime(), key.getType(), patEnt);
        appointmentRepository.save(appointment);
        return value;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof AppointmentSlot slot) {
            return appointmentRepository.existsByDoctorDoctorIdAndDateTime(
                    slot.getDoctor().getDoctorId(), slot.getDateTime());
        }
        return false;
    }

    @Override
    public void clear() {
        appointmentRepository.deleteAll();
    }

    @Override
    public Patient remove(Object key) {
        if (key instanceof AppointmentSlot slot) {
            var existing = appointmentRepository.findByDoctorDoctorIdAndDateTime(
                    slot.getDoctor().getDoctorId(), slot.getDateTime()).orElse(null);
            if (existing != null) {
                appointmentRepository.delete(existing);
                return patientMapper.toDomain(existing.getPatient());
            }
        }
        return null;
    }

    @Override
    public int size() {
        return (int) appointmentRepository.count();
    }

    @Override
    public Set<Entry<AppointmentSlot, Patient>> entrySet() {
        return appointmentRepository.findAll().stream()
                .map(app -> new SimpleEntry<>(
                        new AppointmentSlot(doctorMapper.toDomain(app.getDoctor()), app.getDateTime(), app.getType()),
                        patientMapper.toDomain(app.getPatient())))
                .collect(Collectors.toSet());
    }
}
