package es.brasatech.medpulse.adapters.out.persistence.entity;

import es.brasatech.medpulse.domain.AppointmentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String calendarId;
    private String title;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;

    private boolean avoidOverride = true;
    private boolean avoidStepOver = true;

    private String status = "ACTIVE";
    private String statusNotes;

    private String bookedByUserId;

    @Enumerated(EnumType.STRING)
    private AppointmentType type;
}
