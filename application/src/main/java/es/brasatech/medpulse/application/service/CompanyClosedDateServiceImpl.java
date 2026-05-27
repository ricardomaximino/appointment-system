package es.brasatech.medpulse.application.service;

import es.brasatech.medpulse.application.port.in.*;
import es.brasatech.medpulse.application.port.out.CompanyClosedDatePersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyClosedDateServiceImpl implements AddClosedDateUseCase, RemoveClosedDateUseCase, GetAllClosedDatesUseCase, IsCompanyClosedUseCase {

    private final CompanyClosedDatePersistencePort closedDatePersistencePort;

    @Override
    @Transactional
    public void addClosedDate(LocalDate date) {
        closedDatePersistencePort.save(date);
    }

    @Override
    @Transactional
    public void removeClosedDate(LocalDate date) {
        closedDatePersistencePort.delete(date);
    }

    @Override
    public List<LocalDate> getAllClosedDates() {
        return closedDatePersistencePort.findAll();
    }

    @Override
    public boolean isCompanyClosed(LocalDate date) {
        return closedDatePersistencePort.findAll().contains(date);
    }
}
