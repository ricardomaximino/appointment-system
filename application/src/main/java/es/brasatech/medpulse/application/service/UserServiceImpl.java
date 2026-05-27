package es.brasatech.medpulse.application.service;

import es.brasatech.medpulse.application.port.in.*;
import es.brasatech.medpulse.application.port.out.UserPersistencePort;
import es.brasatech.medpulse.application.port.out.EventPersistencePort;
import es.brasatech.medpulse.domain.User;
import es.brasatech.medpulse.domain.Role;
import es.brasatech.medpulse.domain.Calendar;
import es.brasatech.medpulse.domain.CalendarType;
import es.brasatech.medpulse.domain.Event;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements CreateUserUseCase, UpdateUserUseCase, DeleteUserUseCase, GetUserUseCase, GetAllUsersUseCase, GetUsersByRoleUseCase {

    private final UserPersistencePort userPersistencePort;
    private final GetOrCreateCalendarUseCase getOrCreateCalendarUseCase;
    private final EventPersistencePort eventPersistencePort;

    @Override
    @Transactional
    public User createUser(User user) {
        if (user == null || user.getId() == null || user.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be blank");
        }
        
        // Setup doctor defaults if they are a DOCTOR and don't have availability configured
        if (user.hasRole(Role.DOCTOR)) {
            if (user.getWeeklyAvailability() == null || user.getWeeklyAvailability().isEmpty()) {
                // Setup Mon/Wed/Fri 09:00 - 13:00, 14:00 - 17:00 as default availability for convenience
                var defaultAvail = new java.util.HashMap<DayOfWeek, java.util.List<es.brasatech.medpulse.domain.TimeRange>>();
                java.util.List<es.brasatech.medpulse.domain.TimeRange> intervals = java.util.List.of(
                        new es.brasatech.medpulse.domain.TimeRange(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(13, 0)),
                        new es.brasatech.medpulse.domain.TimeRange(java.time.LocalTime.of(14, 0), java.time.LocalTime.of(17, 0))
                );
                defaultAvail.put(DayOfWeek.MONDAY, intervals);
                defaultAvail.put(DayOfWeek.WEDNESDAY, intervals);
                defaultAvail.put(DayOfWeek.FRIDAY, intervals);
                user.setWeeklyAvailability(defaultAvail);
            }
            if (user.getAppointmentTypes() == null || user.getAppointmentTypes().isEmpty()) {
                user.setAppointmentTypes(java.util.List.of(
                        es.brasatech.medpulse.domain.AppointmentType.SHORT,
                        es.brasatech.medpulse.domain.AppointmentType.MEDIUM
                ));
            }
        }

        User savedUser = userPersistencePort.save(user);

        // Pre-create PROFESSIONAL and PERSONAL calendars for coexisting calendar capabilities
        getOrCreateCalendarUseCase.getOrCreateCalendar(savedUser.getId(), CalendarType.PROFESSIONAL);
        getOrCreateCalendarUseCase.getOrCreateCalendar(savedUser.getId(), CalendarType.PERSONAL);

        return savedUser;
    }

    @Override
    @Transactional
    public User updateUser(User user) {
        return userPersistencePort.save(user);
    }

    @Override
    @Transactional
    public void deleteUser(String id) {
        User user = userPersistencePort.findById(id).orElse(null);
        if (user == null) {
            return;
        }

        if (user.hasRole(Role.DOCTOR)) {
            // Retrieve doctor's professional calendar
            Calendar profCalendar = getOrCreateCalendarUseCase.getOrCreateCalendar(id, CalendarType.PROFESSIONAL);
            List<Event> doctorEvents = eventPersistencePort.findByCalendarId(profCalendar.getId());
            
            // Flag active doctor events as REBOOK for administrative safety
            for (Event event : doctorEvents) {
                if ("ACTIVE".equalsIgnoreCase(event.getStatus())) {
                    event.setStatus("REBOOK");
                    // Unlink calendar so it does not block future bookings
                    event.setCalendarId(null);
                    eventPersistencePort.save(event);
                }
            }
            // Remove calendars
            eventPersistencePort.deleteByCalendarId(profCalendar.getId());
            
            Calendar persCalendar = getOrCreateCalendarUseCase.getOrCreateCalendar(id, CalendarType.PERSONAL);
            eventPersistencePort.deleteByCalendarId(persCalendar.getId());
        }

        if (user.hasRole(Role.PATIENT)) {
            // Delete all events booked by this patient
            eventPersistencePort.deleteByBookedByUserId(id);
        }

        userPersistencePort.deleteById(id);
    }

    @Override
    public User getUser(String id) {
        return userPersistencePort.findById(id).orElse(null);
    }

    @Override
    public List<User> getAllUsers() {
        return userPersistencePort.findAll();
    }

    @Override
    public List<User> getUsersByRole(Role role) {
        return userPersistencePort.findByRole(role);
    }
}
