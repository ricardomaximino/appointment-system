package es.brasatech.medpulse.service.delegation;

import es.brasatech.medpulse.domain.Doctor;
import es.brasatech.medpulse.mapper.DoctorMapper;
import es.brasatech.medpulse.repository.DoctorRepository;

import java.util.AbstractMap;
import java.util.Set;
import java.util.stream.Collectors;

public class DoctorDelegationMap extends AbstractMap<String, Doctor> {
    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;

    public DoctorDelegationMap(DoctorRepository doctorRepository, DoctorMapper doctorMapper) {
        this.doctorRepository = doctorRepository;
        this.doctorMapper = doctorMapper;
    }

    @Override
    public Doctor get(Object key) {
        if (key instanceof String) {
            return doctorRepository.findById((String) key)
                    .map(doctorMapper::toDomain)
                    .orElse(null);
        }
        return null;
    }

    @Override
    public Doctor put(String key, Doctor value) {
        var entity = doctorMapper.toEntity(value);
        doctorRepository.save(entity);
        return value;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof String) {
            return doctorRepository.existsById((String) key);
        }
        return false;
    }

    @Override
    public void clear() {
        doctorRepository.deleteAll();
    }

    @Override
    public Doctor remove(Object key) {
        if (key instanceof String) {
            var existing = get(key);
            if (existing != null) {
                doctorRepository.deleteById((String) key);
                return existing;
            }
        }
        return null;
    }

    @Override
    public int size() {
        return (int) doctorRepository.count();
    }

    @Override
    public Set<Entry<String, Doctor>> entrySet() {
        return doctorRepository.findAll().stream()
                .map(doctorMapper::toDomain)
                .map(doc -> new SimpleEntry<>(doc.getDoctorId(), doc))
                .collect(Collectors.toSet());
    }
}
