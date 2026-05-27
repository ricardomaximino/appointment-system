package es.brasatech.medpulse.repository;

import es.brasatech.medpulse.domain.*;
import es.brasatech.medpulse.entity.*;
import es.brasatech.medpulse.mapper.DoctorMapper;
import es.brasatech.medpulse.mapper.PatientMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final CompanyClosedDateRepository companyClosedDateRepository;
    private final DoctorMapper doctorMapper;
    private final PatientMapper patientMapper;

    public DatabaseSeeder(DoctorRepository doctorRepository,
                          PatientRepository patientRepository,
                          CompanyClosedDateRepository companyClosedDateRepository,
                          DoctorMapper doctorMapper,
                          PatientMapper patientMapper) {
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.companyClosedDateRepository = companyClosedDateRepository;
        this.doctorMapper = doctorMapper;
        this.patientMapper = patientMapper;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (doctorRepository.count() > 0) {
            return; // Already seeded
        }

        // 1. Seed Domain Doctors and map to Entities
        var houseAvailability = Map.of(
                DayOfWeek.MONDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), new TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0))),
                DayOfWeek.WEDNESDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), new TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0))),
                DayOfWeek.FRIDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), new TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0)))
        );
        var doc1 = new Doctor("doc1", "Dr. House", List.of(AppointmentType.SHORT, AppointmentType.MEDIUM));
        doc1.setWeeklyAvailability(houseAvailability);

        var greyAvailability = Map.of(
                DayOfWeek.TUESDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0)), new TimeRange(LocalTime.of(13, 0), LocalTime.of(16, 0))),
                DayOfWeek.THURSDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0)), new TimeRange(LocalTime.of(13, 0), LocalTime.of(16, 0))),
                DayOfWeek.SATURDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0)))
        );
        var doc2 = new Doctor("doc2", "Dr. Grey", List.of(AppointmentType.MEDIUM, AppointmentType.LONG));
        doc2.setWeeklyAvailability(greyAvailability);

        var strangeAvailability = Map.of(
                LocalDate.now().plusDays(2), List.of(new TimeRange(LocalTime.of(8, 30), LocalTime.of(12, 0)), new TimeRange(LocalTime.of(17, 0), LocalTime.of(18, 0))),
                LocalDate.now().plusDays(5), List.of(new TimeRange(LocalTime.of(11, 0), LocalTime.of(17, 0)))
        );
        var doc3 = new Doctor("doc3", "Dr. Strange", List.of(AppointmentType.SHORT, AppointmentType.MEDIUM, AppointmentType.LONG));
        doc3.setSpecificDatesAvailability(strangeAvailability);

        // Convert domain objects to entities via MapStruct
        DoctorEntity docEnt1 = doctorMapper.toEntity(doc1);
        DoctorEntity docEnt2 = doctorMapper.toEntity(doc2);
        DoctorEntity docEnt3 = doctorMapper.toEntity(doc3);

        doctorRepository.saveAll(List.of(docEnt1, docEnt2, docEnt3));

        // 2. Patients
        var pat1 = new Patient("pat1", "John Doe");
        var pat2 = new Patient("pat2", "Jane Smith");
        
        PatientEntity patEnt1 = patientMapper.toEntity(pat1);
        PatientEntity patEnt2 = patientMapper.toEntity(pat2);

        patientRepository.saveAll(List.of(patEnt1, patEnt2));

        // 3. Closed dates
        companyClosedDateRepository.save(new CompanyClosedDateEntity(LocalDate.now().plusDays(15)));
    }
}
