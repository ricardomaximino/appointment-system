package es.brasatech.medpulse.application.port.in;

import es.brasatech.medpulse.domain.Calendar;
import java.util.List;

public interface GetCalendarsByUserUseCase {
    List<Calendar> getCalendarsByUser(String userId);
}
