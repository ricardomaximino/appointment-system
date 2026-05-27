package es.brasatech.medpulse.application.port.in;

import java.time.LocalDate;

public interface IsCompanyClosedUseCase {
    boolean isCompanyClosed(LocalDate date);
}
