package es.brasatech.medpulse.domain;

import java.time.LocalTime;

public record TimeRange(LocalTime start, LocalTime end) {
    public boolean contains(LocalTime tStart, LocalTime tEnd) {
        return !tStart.isBefore(start) && !tEnd.isAfter(end);
    }
}
