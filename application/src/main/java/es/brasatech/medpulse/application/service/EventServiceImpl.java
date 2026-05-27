package es.brasatech.medpulse.application.service;

import es.brasatech.medpulse.application.port.in.*;
import es.brasatech.medpulse.application.port.out.EventPersistencePort;
import es.brasatech.medpulse.application.port.out.SecurityContextPort;
import es.brasatech.medpulse.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements CreateEventUseCase, UpdateEventUseCase, DeleteEventUseCase, CancelEventUseCase, GetEventUseCase, GetAllEventsUseCase, GetEventsByCalendarUseCase, GetEventsForUserUseCase, CheckEventConflictUseCase, GetAvailableSlotsUseCase, ClearBookingsUseCase {

    private final EventPersistencePort eventPersistencePort;
    private final GetCalendarUseCase getCalendarUseCase;
    private final GetCalendarsByUserUseCase getCalendarsByUserUseCase;
    private final GetOrCreateCalendarUseCase getOrCreateCalendarUseCase;
    private final GetUserUseCase getUserUseCase;
    private final IsCompanyClosedUseCase isCompanyClosedUseCase;
    private final SecurityContextPort securityContextPort;

    // Thread-safe lock registry mapped by doctor/user ID for StampedLock concurrency control
    private static final Map<String, StampedLock> userLocks = new ConcurrentHashMap<>();

    private StampedLock getLockForUser(String userId) {
        return userLocks.computeIfAbsent(userId, id -> new StampedLock());
    }

    @Override
    @Transactional
    public Event createEvent(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        Calendar calendar = getCalendarUseCase.getCalendar(event.getCalendarId());
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar not found: " + event.getCalendarId());
        }

        String userId = calendar.getUserId();
        StampedLock lock = getLockForUser(userId);
        long stamp = lock.writeLock();
        try {
            // 1. Check closed date for professional calendar events
            if (CalendarType.PROFESSIONAL.equals(calendar.getType())) {
                LocalDate eventDate = event.getStartDateTime().toLocalDate();
                if (isCompanyClosedUseCase.isCompanyClosed(eventDate)) {
                    throw new IllegalStateException("Cannot schedule on a closed date: " + eventDate);
                }

                // 2. Validate shifts for doctors
                User user = getUserUseCase.getUser(userId);
                if (user != null && user.hasRole(Role.DOCTOR)) {
                    validateDoctorShift(user, event);
                }
            }

            // 3. Check for overlaps / conflicts
            if (hasConflict(event)) {
                throw new IllegalStateException("Scheduling conflict detected! Overlapping event with blocking settings exists.");
            }

            // Artificial tiny delay to simulate core business logic validation processing (like monolithic code)
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return eventPersistencePort.save(event);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    @Transactional
    public Event updateEvent(Event event) {
        if (event == null || event.getId() == null) {
            throw new IllegalArgumentException("Event and Event ID cannot be null");
        }

        Calendar calendar = getCalendarUseCase.getCalendar(event.getCalendarId());
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar not found");
        }

        String userId = calendar.getUserId();
        StampedLock lock = getLockForUser(userId);
        long stamp = lock.writeLock();
        try {
            if (CalendarType.PROFESSIONAL.equals(calendar.getType())) {
                LocalDate eventDate = event.getStartDateTime().toLocalDate();
                if (isCompanyClosedUseCase.isCompanyClosed(eventDate)) {
                    throw new IllegalStateException("Cannot schedule on a closed date: " + eventDate);
                }

                User user = getUserUseCase.getUser(userId);
                if (user != null && user.hasRole(Role.DOCTOR)) {
                    validateDoctorShift(user, event);
                }
            }

            if (hasConflict(event)) {
                throw new IllegalStateException("Scheduling conflict detected! Overlapping event exists.");
            }

            return eventPersistencePort.save(event);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    @Transactional
    public void deleteEvent(Long id) {
        eventPersistencePort.deleteById(id);
    }

    @Override
    @Transactional
    public void cancelEvent(Long id) {
        eventPersistencePort.findById(id).ifPresent(event -> {
            event.setStatus("CANCEL");
            eventPersistencePort.save(event);
        });
    }

    @Override
    public Event getEvent(Long id) {
        return eventPersistencePort.findById(id).orElse(null);
    }

    @Override
    public List<Event> getAllEvents() {
        List<Event> events = eventPersistencePort.findAll();
        String currentUserId = securityContextPort.getCurrentUserId();
        for (Event event : events) {
            if (event.getCalendarId() != null) {
                Calendar calendar = getCalendarUseCase.getCalendar(event.getCalendarId());
                if (calendar != null && !calendar.getUserId().equals(currentUserId)) {
                    if (CalendarType.PERSONAL.equals(calendar.getType())) {
                        event.setTitle("Unavailable - Personal Time");
                        event.setBookedByUserId(null);
                        event.setStatusNotes(null);
                    }
                }
            }
        }
        return events;
    }

    @Override
    public List<Event> getEventsByCalendar(String calendarId) {
        List<Event> events = eventPersistencePort.findByCalendarId(calendarId);
        Calendar calendar = getCalendarUseCase.getCalendar(calendarId);
        if (calendar == null) {
            return List.of();
        }
        String currentUserId = securityContextPort.getCurrentUserId();
        if (calendar.getUserId().equals(currentUserId)) {
            return events;
        }
        if (CalendarType.PERSONAL.equals(calendar.getType())) {
            for (Event event : events) {
                event.setTitle("Unavailable - Personal Time");
                event.setBookedByUserId(null);
                event.setStatusNotes(null);
            }
        }
        return events;
    }

    @Override
    public List<Event> getEventsForUser(String userId) {
        List<Event> allEvents = eventPersistencePort.findByUserId(userId);
        String currentUserId = securityContextPort.getCurrentUserId();
        if (userId.equals(currentUserId)) {
            return allEvents;
        }
        for (Event event : allEvents) {
            if (event.getCalendarId() != null) {
                Calendar calendar = getCalendarUseCase.getCalendar(event.getCalendarId());
                if (calendar != null && CalendarType.PERSONAL.equals(calendar.getType())) {
                    event.setTitle("Unavailable - Personal Time");
                    event.setBookedByUserId(null);
                    event.setStatusNotes(null);
                }
            }
        }
        return allEvents;
    }

    @Override
    public boolean hasConflict(Event event) {
        Calendar calendar = getCalendarUseCase.getCalendar(event.getCalendarId());
        if (calendar == null) {
            return false;
        }

        // We check conflicts in ALL calendars belonging to the owner user
        List<Calendar> userCalendars = getCalendarsByUserUseCase.getCalendarsByUser(calendar.getUserId());
        List<Event> allUserEvents = new ArrayList<>();
        for (Calendar cal : userCalendars) {
            allUserEvents.addAll(eventPersistencePort.findByCalendarId(cal.getId()));
        }

        // If this is a professional event, we also verify conflicts in the patient's calendars
        if (event.getBookedByUserId() != null) {
            List<Calendar> patientCalendars = getCalendarsByUserUseCase.getCalendarsByUser(event.getBookedByUserId());
            for (Calendar cal : patientCalendars) {
                allUserEvents.addAll(eventPersistencePort.findByCalendarId(cal.getId()));
            }
        }

        LocalDateTime startNew = event.getStartDateTime();
        LocalDateTime endNew = event.getEndDateTime();

        for (Event existing : allUserEvents) {
            // Ignore cancelled, inactive, or self
            if (existing.getId() != null && existing.getId().equals(event.getId())) {
                continue;
            }
            if ("CANCEL".equalsIgnoreCase(existing.getStatus()) || "CANCELLED".equalsIgnoreCase(existing.getStatus())) {
                continue;
            }

            LocalDateTime startExist = existing.getStartDateTime();
            LocalDateTime endExist = existing.getEndDateTime();

            // Overlap condition: startNew < endExist AND startExist < endNew
            if (startNew.isBefore(endExist) && startExist.isBefore(endNew)) {
                // Conflict occurs if EITHER event requires avoiding overrides OR avoids step-overs
                if (event.isAvoidOverride() || existing.isAvoidOverride()) {
                    return true;
                }
                if (event.isAvoidStepOver() || existing.isAvoidStepOver()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<LocalDateTime> getAvailableSlots(String doctorId, LocalDate date) {
        User doctor = getUserUseCase.getUser(doctorId);
        AppointmentType smallestType = AppointmentType.SHORT;
        if (doctor != null && !doctor.getAppointmentTypes().isEmpty()) {
            smallestType = doctor.getAppointmentTypes().stream()
                    .min((t1, t2) -> t1.getDuration().compareTo(t2.getDuration()))
                    .orElse(AppointmentType.SHORT);
        }
        return getAvailableSlots(doctorId, date, smallestType);
    }

    @Override
    public List<LocalDateTime> getAvailableSlots(String doctorId, LocalDate date, AppointmentType type) {
        StampedLock lock = getLockForUser(doctorId);
        long stamp = lock.readLock();
        try {
            // 1. Check closed date
            if (isCompanyClosedUseCase.isCompanyClosed(date)) {
                return List.of();
            }

            // 2. Fetch doctor profile
            User doctor = getUserUseCase.getUser(doctorId);
            if (doctor == null || !doctor.hasRole(Role.DOCTOR)) {
                throw new IllegalArgumentException("Doctor not found: " + doctorId);
            }

            // 3. Resolve shifts
            List<TimeRange> shifts = null;
            if (doctor.getSpecificDatesAvailability() != null && doctor.getSpecificDatesAvailability().containsKey(date)) {
                shifts = doctor.getSpecificDatesAvailability().get(date);
            } else {
                DayOfWeek dayOfWeek = date.getDayOfWeek();
                if (doctor.getWeeklyAvailability() != null && doctor.getWeeklyAvailability().containsKey(dayOfWeek)) {
                    shifts = doctor.getWeeklyAvailability().get(dayOfWeek);
                }
            }

            if (shifts == null || shifts.isEmpty()) {
                return List.of();
            }

            // Obtain professional calendar ID to model hypothetical event conflicts
            Calendar profCalendar = getOrCreateCalendarUseCase.getOrCreateCalendar(doctorId, CalendarType.PROFESSIONAL);

            List<LocalDateTime> availableSlots = new ArrayList<>();
            long durationMinutes = type.getDuration().toMinutes();

            // 4. Generate starting times matching the shifts
            for (TimeRange shift : shifts) {
                LocalTime time = shift.start();
                while (time.isBefore(shift.end())) {
                    LocalDateTime candidateStart = date.atTime(time);
                    LocalDateTime candidateEnd = candidateStart.plusMinutes(durationMinutes);

                    if (candidateEnd.toLocalTime().isAfter(shift.end())) {
                        break;
                    }

                    // Create test candidate Event (avoids overrides/stepovers by default)
                    Event candidate = new Event(null, profCalendar.getId(), "Candidate Slot", candidateStart, candidateEnd, type);

                    if (!hasConflict(candidate)) {
                        availableSlots.add(candidateStart);
                    }

                    // Increment time by selected slot duration
                    time = time.plusMinutes(durationMinutes);
                }
            }

            return availableSlots;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    @Transactional
    public void clearBookings() {
        eventPersistencePort.clearAll();
    }

    private void validateDoctorShift(User doctor, Event event) {
        LocalDateTime startDateTime = event.getStartDateTime();
        LocalDate appointmentDate = startDateTime.toLocalDate();
        LocalTime startTime = startDateTime.toLocalTime();
        LocalTime endTime = event.getEndDateTime().toLocalTime();

        List<TimeRange> shifts = null;
        if (doctor.getSpecificDatesAvailability() != null && doctor.getSpecificDatesAvailability().containsKey(appointmentDate)) {
            shifts = doctor.getSpecificDatesAvailability().get(appointmentDate);
        } else {
            DayOfWeek dayOfWeek = startDateTime.getDayOfWeek();
            if (doctor.getWeeklyAvailability() != null && doctor.getWeeklyAvailability().containsKey(dayOfWeek)) {
                shifts = doctor.getWeeklyAvailability().get(dayOfWeek);
            }
        }

        if (shifts == null || shifts.isEmpty()) {
            throw new IllegalStateException("Booking failed: Doctor %s does not work on %s".formatted(doctor.getName(), appointmentDate));
        }

        boolean fitsInShift = false;
        for (TimeRange shift : shifts) {
            if (shift.contains(startTime, endTime)) {
                fitsInShift = true;
                break;
            }
        }

        if (!fitsInShift) {
            throw new IllegalStateException("Booking failed: Desired time %s - %s is outside Doctor %s's working shifts for %s".formatted(
                    startTime, endTime, doctor.getName(), appointmentDate));
        }
    }
}
