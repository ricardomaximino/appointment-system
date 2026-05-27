package es.brasatech.medpulse.adapters.out.persistence.mapper;

import es.brasatech.medpulse.adapters.out.persistence.entity.*;
import es.brasatech.medpulse.domain.*;
import es.brasatech.medpulse.domain.Calendar;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class DomainMapper {

    // --- User Mapping ---

    public User toDomain(UserEntity entity) {
        if (entity == null) {
            return null;
        }

        User user = new User();
        user.setId(entity.getId());
        user.setName(entity.getName());
        user.setPassword(entity.getPassword());
        user.setRoles(new HashSet<>(entity.getRoles()));
        user.setAppointmentTypes(new ArrayList<>(entity.getAppointmentTypes()));

        // Map weekly availability list to domain map
        Map<DayOfWeek, List<TimeRange>> weeklyMap = new HashMap<>();
        if (entity.getWeeklyAvailabilityList() != null) {
            for (UserWeeklyAvailabilityEntity wa : entity.getWeeklyAvailabilityList()) {
                weeklyMap.computeIfAbsent(wa.getDayOfWeek(), k -> new ArrayList<>())
                         .add(new TimeRange(wa.getStartTime(), wa.getEndTime()));
            }
        }
        user.setWeeklyAvailability(weeklyMap);

        // Map specific dates availability list to domain map
        Map<LocalDate, List<TimeRange>> specificMap = new HashMap<>();
        if (entity.getSpecificDatesAvailabilityList() != null) {
            for (UserSpecificAvailabilityEntity sa : entity.getSpecificDatesAvailabilityList()) {
                specificMap.computeIfAbsent(sa.getDate(), k -> new ArrayList<>())
                           .add(new TimeRange(sa.getStartTime(), sa.getEndTime()));
            }
        }
        user.setSpecificDatesAvailability(specificMap);

        return user;
    }

    public UserEntity toEntity(User domain) {
        if (domain == null) {
            return null;
        }

        UserEntity entity = new UserEntity();
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setPassword(domain.getPassword());
        entity.setRoles(new HashSet<>(domain.getRoles()));
        entity.setAppointmentTypes(new ArrayList<>(domain.getAppointmentTypes()));

        // Map weekly availability
        List<UserWeeklyAvailabilityEntity> weeklyList = new ArrayList<>();
        if (domain.getWeeklyAvailability() != null) {
            for (Map.Entry<DayOfWeek, List<TimeRange>> entry : domain.getWeeklyAvailability().entrySet()) {
                for (TimeRange tr : entry.getValue()) {
                    UserWeeklyAvailabilityEntity wa = new UserWeeklyAvailabilityEntity();
                    wa.setUser(entity);
                    wa.setDayOfWeek(entry.getKey());
                    wa.setStartTime(tr.start());
                    wa.setEndTime(tr.end());
                    weeklyList.add(wa);
                }
            }
        }
        entity.setWeeklyAvailabilityList(weeklyList);

        // Map specific dates
        List<UserSpecificAvailabilityEntity> specificList = new ArrayList<>();
        if (domain.getSpecificDatesAvailability() != null) {
            for (Map.Entry<LocalDate, List<TimeRange>> entry : domain.getSpecificDatesAvailability().entrySet()) {
                for (TimeRange tr : entry.getValue()) {
                    UserSpecificAvailabilityEntity sa = new UserSpecificAvailabilityEntity();
                    sa.setUser(entity);
                    sa.setDate(entry.getKey());
                    sa.setStartTime(tr.start());
                    sa.setEndTime(tr.end());
                    specificList.add(sa);
                }
            }
        }
        entity.setSpecificDatesAvailabilityList(specificList);

        return entity;
    }

    // --- Calendar Mapping ---

    public Calendar toDomain(CalendarEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Calendar(entity.getId(), entity.getName(), entity.getType(), entity.getUserId());
    }

    public CalendarEntity toEntity(Calendar domain) {
        if (domain == null) {
            return null;
        }
        return new CalendarEntity(domain.getId(), domain.getName(), domain.getType(), domain.getUserId());
    }

    // --- Event Mapping ---

    public Event toDomain(EventEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Event(
                entity.getId(),
                entity.getCalendarId(),
                entity.getTitle(),
                entity.getStartDateTime(),
                entity.getEndDateTime(),
                entity.isAvoidOverride(),
                entity.isAvoidStepOver(),
                entity.getStatus(),
                entity.getStatusNotes(),
                entity.getBookedByUserId(),
                entity.getType()
        );
    }

    public EventEntity toEntity(Event domain) {
        if (domain == null) {
            return null;
        }
        return new EventEntity(
                domain.getId(),
                domain.getCalendarId(),
                domain.getTitle(),
                domain.getStartDateTime(),
                domain.getEndDateTime(),
                domain.isAvoidOverride(),
                domain.isAvoidStepOver(),
                domain.getStatus(),
                domain.getStatusNotes(),
                domain.getBookedByUserId(),
                domain.getType()
        );
    }
}
