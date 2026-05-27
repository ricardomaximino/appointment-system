package es.brasatech.medpulse.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.EqualsAndHashCode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class User {

    private String id;
    private String name;
    private String password = "password";
    private Set<Role> roles = new HashSet<>();

    // Doctor-specific availability features embedded cleanly inside the domain User
    private List<AppointmentType> appointmentTypes = new ArrayList<>();
    private Map<DayOfWeek, List<TimeRange>> weeklyAvailability = new HashMap<>();
    private Map<LocalDate, List<TimeRange>> specificDatesAvailability = new HashMap<>();

    public User(String id, String name, Set<Role> roles) {
        this.id = id;
        this.name = name;
        this.roles = roles;
    }

    public boolean hasRole(Role role) {
        return roles != null && roles.contains(role);
    }
}
