package es.brasatech.medpulse.domain;

import java.time.Duration;

public enum AppointmentType {
    SHORT(30L),
    MEDIUM(60L),
    LONG(90L);

    private final long duration;

    AppointmentType(long duration) {
        this.duration = duration;
    }

    public Duration getDuration() {
        return Duration.ofMinutes(this.duration);
    }
}