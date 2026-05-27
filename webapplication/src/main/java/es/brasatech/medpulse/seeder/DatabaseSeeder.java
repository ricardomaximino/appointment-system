package es.brasatech.medpulse.seeder;

import es.brasatech.medpulse.application.port.in.*;
import es.brasatech.medpulse.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final GetAllUsersUseCase getAllUsersUseCase;
    private final CreateUserUseCase createUserUseCase;
    private final AddClosedDateUseCase addClosedDateUseCase;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!getAllUsersUseCase.getAllUsers().isEmpty()) {
            return; // Already seeded
        }

        // 1. Seed Doctor: Dr. House
        var houseAvailability = Map.of(
                DayOfWeek.MONDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), new TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0))),
                DayOfWeek.WEDNESDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), new TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0))),
                DayOfWeek.FRIDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), new TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0)))
        );
        User doc1 = new User("doc1", "Dr. House", Set.of(Role.DOCTOR));
        doc1.setWeeklyAvailability(houseAvailability);
        doc1.setAppointmentTypes(List.of(AppointmentType.SHORT, AppointmentType.MEDIUM));
        createUserUseCase.createUser(doc1);

        // 2. Seed Doctor: Dr. Grey
        var greyAvailability = Map.of(
                DayOfWeek.TUESDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0)), new TimeRange(LocalTime.of(13, 0), LocalTime.of(16, 0))),
                DayOfWeek.THURSDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0)), new TimeRange(LocalTime.of(13, 0), LocalTime.of(16, 0))),
                DayOfWeek.SATURDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0)))
        );
        User doc2 = new User("doc2", "Dr. Grey", Set.of(Role.DOCTOR));
        doc2.setWeeklyAvailability(greyAvailability);
        doc2.setAppointmentTypes(List.of(AppointmentType.MEDIUM, AppointmentType.LONG));
        createUserUseCase.createUser(doc2);

        // 3. Seed Doctor: Dr. Strange (with date-specific availabilities)
        var strangeAvailability = Map.of(
                LocalDate.now().plusDays(2), List.of(new TimeRange(LocalTime.of(8, 30), LocalTime.of(12, 0)), new TimeRange(LocalTime.of(17, 0), LocalTime.of(18, 0))),
                LocalDate.now().plusDays(5), List.of(new TimeRange(LocalTime.of(11, 0), LocalTime.of(17, 0)))
        );
        User doc3 = new User("doc3", "Dr. Strange", Set.of(Role.DOCTOR));
        doc3.setSpecificDatesAvailability(strangeAvailability);
        doc3.setAppointmentTypes(List.of(AppointmentType.SHORT, AppointmentType.MEDIUM, AppointmentType.LONG));
        createUserUseCase.createUser(doc3);

        // 4. Seed Patients
        User pat1 = new User("pat1", "John Doe", Set.of(Role.PATIENT));
        createUserUseCase.createUser(pat1);

        User pat2 = new User("pat2", "Jane Smith", Set.of(Role.PATIENT));
        createUserUseCase.createUser(pat2);

        // 5. Closed dates
        addClosedDateUseCase.addClosedDate(LocalDate.now().plusDays(15));

        // 6. Seed Admin
        User admin = new User("admin", "System Admin", Set.of(Role.ADMIN));
        createUserUseCase.createUser(admin);
    }
}
