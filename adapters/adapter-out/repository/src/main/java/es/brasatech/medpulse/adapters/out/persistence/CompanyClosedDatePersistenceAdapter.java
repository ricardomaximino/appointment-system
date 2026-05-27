package es.brasatech.medpulse.adapters.out.persistence;

import es.brasatech.medpulse.application.port.out.CompanyClosedDatePersistencePort;
import es.brasatech.medpulse.adapters.out.persistence.entity.CompanyClosedDateEntity;
import es.brasatech.medpulse.adapters.out.persistence.repository.CompanyClosedDateEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CompanyClosedDatePersistenceAdapter implements CompanyClosedDatePersistencePort {

    private final CompanyClosedDateEntityRepository closedDateEntityRepository;

    @Override
    public void save(LocalDate date) {
        closedDateEntityRepository.save(new CompanyClosedDateEntity(date));
    }

    @Override
    public void delete(LocalDate date) {
        closedDateEntityRepository.deleteById(date);
    }

    @Override
    public List<LocalDate> findAll() {
        return closedDateEntityRepository.findAll().stream()
                .map(CompanyClosedDateEntity::getClosedDate)
                .collect(Collectors.toList());
    }
}
