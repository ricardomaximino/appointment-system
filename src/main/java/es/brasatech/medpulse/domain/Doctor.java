package es.brasatech.medpulse.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.EqualsAndHashCode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Doctor {

    private String doctorId;
    private String name;
    private List<AppointmentType> appointmentTypes = new ArrayList<>();
    private Map<DayOfWeek, List<TimeRange>> weeklyAvailability = new HashMap<>();
    private Map<LocalDate, List<TimeRange>> specificDatesAvailability = new HashMap<>();

    public Doctor(String doctorId, String name, List<AppointmentType> appointmentTypes) {
        this.doctorId = doctorId;
        this.name = name;
        this.appointmentTypes = appointmentTypes;
    }
}