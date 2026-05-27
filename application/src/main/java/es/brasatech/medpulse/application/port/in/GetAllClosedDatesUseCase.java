package es.brasatech.medpulse.application.port.in;

import java.time.LocalDate;
import java.util.List;

public interface GetAllClosedDatesUseCase {
    List<LocalDate> getAllClosedDates();
}
