package es.brasatech.medpulse.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Event {

    private Long id;
    private String calendarId;
    private String title;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;

    // Redesign override configuration checkboxes
    private boolean avoidOverride = true;
    private boolean avoidStepOver = true;

    // Status: e.g., ACTIVE, CANCEL, REBOOK
    private String status = "ACTIVE";
    private String statusNotes;

    // Patient booking reference
    private String bookedByUserId;

    // AppointmentType for backward compatibility and duration slot sizing
    private AppointmentType type;

    public Event(Long id, String calendarId, String title, LocalDateTime startDateTime, LocalDateTime endDateTime, AppointmentType type) {
        this.id = id;
        this.calendarId = calendarId;
        this.title = title;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.type = type;
        this.avoidOverride = true;
        this.avoidStepOver = true;
        this.status = "ACTIVE";
    }

    public Event(Long id, String calendarId, String title, LocalDateTime startDateTime, LocalDateTime endDateTime, boolean avoidOverride, boolean avoidStepOver, String status, String bookedByUserId, AppointmentType type) {
        this.id = id;
        this.calendarId = calendarId;
        this.title = title;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.avoidOverride = avoidOverride;
        this.avoidStepOver = avoidStepOver;
        this.status = status;
        this.bookedByUserId = bookedByUserId;
        this.type = type;
    }
}
