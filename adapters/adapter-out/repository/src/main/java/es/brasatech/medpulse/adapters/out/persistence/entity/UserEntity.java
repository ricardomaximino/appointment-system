package es.brasatech.medpulse.adapters.out.persistence.entity;

import es.brasatech.medpulse.domain.AppointmentType;
import es.brasatech.medpulse.domain.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

    @Id
    private String id;
    private String name;
    private String password = "password";

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_appointment_types", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "appointment_type")
    @Enumerated(EnumType.STRING)
    private List<AppointmentType> appointmentTypes = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<UserWeeklyAvailabilityEntity> weeklyAvailabilityList = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<UserSpecificAvailabilityEntity> specificDatesAvailabilityList = new ArrayList<>();
}
