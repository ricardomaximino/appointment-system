package es.brasatech.medpulse.service.delegation;

import es.brasatech.medpulse.domain.Patient;
import es.brasatech.medpulse.mapper.PatientMapper;
import es.brasatech.medpulse.repository.PatientRepository;

import java.util.AbstractMap;
import java.util.Set;
import java.util.stream.Collectors;

public class PatientDelegationMap extends AbstractMap<String, Patient> {
    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    public PatientDelegationMap(PatientRepository patientRepository, PatientMapper patientMapper) {
        this.patientRepository = patientRepository;
        this.patientMapper = patientMapper;
    }

    @Override
    public Patient get(Object key) {
        if (key instanceof String) {
            return patientRepository.findById((String) key)
                    .map(patientMapper::toDomain)
                    .orElse(null);
        }
        return null;
    }

    @Override
    public Patient put(String key, Patient value) {
        var entity = patientMapper.toEntity(value);
        patientRepository.save(entity);
        return value;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof String) {
            return patientRepository.existsById((String) key);
        }
        return false;
    }

    @Override
    public void clear() {
        patientRepository.deleteAll();
    }

    @Override
    public Patient remove(Object key) {
        if (key instanceof String) {
            var existing = get(key);
            if (existing != null) {
                patientRepository.deleteById((String) key);
                return existing;
            }
        }
        return null;
    }

    @Override
    public int size() {
        return (int) patientRepository.count();
    }

    @Override
    public Set<Entry<String, Patient>> entrySet() {
        return patientRepository.findAll().stream()
                .map(patientMapper::toDomain)
                .map(pat -> new SimpleEntry<>(pat.getPatientId(), pat))
                .collect(Collectors.toSet());
    }
}
