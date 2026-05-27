package es.brasatech.medpulse.application.port.in;

import java.time.LocalDate;

public interface AddClosedDateUseCase {
    void addClosedDate(LocalDate date);
}
