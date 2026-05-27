package es.brasatech.medpulse.adapters.out.persistence.entity;

import es.brasatech.medpulse.domain.CalendarType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "calendars")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEntity {

    @Id
    private String id;
    private String name;

    @Enumerated(EnumType.STRING)
    private CalendarType type;

    private String userId;
}
