package es.brasatech.medpulse.domain;

import java.time.LocalDateTime;

public record AppointmentSlot(Doctor doctor, LocalDateTime dateTime, AppointmentType type) {}