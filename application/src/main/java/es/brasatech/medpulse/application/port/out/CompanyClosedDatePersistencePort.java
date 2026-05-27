package es.brasatech.medpulse.application.port.out;

import java.time.LocalDate;
import java.util.List;

public interface CompanyClosedDatePersistencePort {
    void save(LocalDate date);
    void delete(LocalDate date);
    List<LocalDate> findAll();
}
