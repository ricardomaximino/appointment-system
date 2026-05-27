package es.brasatech.medpulse.service.delegation;

import es.brasatech.medpulse.entity.CompanyClosedDateEntity;
import es.brasatech.medpulse.repository.CompanyClosedDateRepository;

import java.time.LocalDate;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.stream.Collectors;

public class ClosedDateDelegationSet extends AbstractSet<LocalDate> {
    private final CompanyClosedDateRepository repository;

    public ClosedDateDelegationSet(CompanyClosedDateRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean add(LocalDate date) {
        if (date == null) return false;
        if (contains(date)) return false;
        repository.save(new CompanyClosedDateEntity(date));
        return true;
    }

    @Override
    public boolean remove(Object o) {
        if (o instanceof LocalDate date) {
            if (contains(date)) {
                repository.deleteById(date);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof LocalDate date) {
            return repository.existsById(date);
        }
        return false;
    }

    @Override
    public void clear() {
        repository.deleteAll();
    }

    @Override
    public Iterator<LocalDate> iterator() {
        return repository.findAll().stream()
                .map(CompanyClosedDateEntity::getClosedDate)
                .collect(Collectors.toList())
                .iterator();
    }

    @Override
    public int size() {
        return (int) repository.count();
    }
}
