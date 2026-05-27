package es.brasatech.medpulse.application.port.in;

import es.brasatech.medpulse.domain.AppointmentType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface GetAvailableSlotsUseCase {
    List<LocalDateTime> getAvailableSlots(String doctorId, LocalDate date);
    List<LocalDateTime> getAvailableSlots(String doctorId, LocalDate date, AppointmentType type);
}
