package es.brasatech.medpulse.application.port.in;

import java.time.LocalDate;

public interface RemoveClosedDateUseCase {
    void removeClosedDate(LocalDate date);
}
