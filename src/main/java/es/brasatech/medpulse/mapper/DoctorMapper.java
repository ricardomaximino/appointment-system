package es.brasatech.medpulse.mapper;

import es.brasatech.medpulse.domain.Doctor;
import es.brasatech.medpulse.domain.TimeRange;
import es.brasatech.medpulse.entity.DoctorEntity;
import es.brasatech.medpulse.entity.DoctorSpecificAvailabilityEntity;
import es.brasatech.medpulse.entity.DoctorWeeklyAvailabilityEntity;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@Mapper(componentModel = "spring")
public interface DoctorMapper {

    DoctorMapper INSTANCE = Mappers.getMapper(DoctorMapper.class);

    @Mapping(target = "weeklyAvailabilityList", source = "weeklyAvailability")
    @Mapping(target = "specificDatesAvailabilityList", source = "specificDatesAvailability")
    DoctorEntity toEntity(Doctor doctor);

    @Mapping(target = "weeklyAvailability", source = "weeklyAvailabilityList")
    @Mapping(target = "specificDatesAvailability", source = "specificDatesAvailabilityList")
    Doctor toDomain(DoctorEntity entity);

    @AfterMapping
    default void linkRelations(@MappingTarget DoctorEntity doctorEntity) {
        if (doctorEntity.getWeeklyAvailabilityList() != null) {
            for (var av : doctorEntity.getWeeklyAvailabilityList()) {
                av.setDoctor(doctorEntity);
            }
        }
        if (doctorEntity.getSpecificDatesAvailabilityList() != null) {
            for (var av : doctorEntity.getSpecificDatesAvailabilityList()) {
                av.setDoctor(doctorEntity);
            }
        }
    }

    default List<DoctorWeeklyAvailabilityEntity> mapWeekly(Map<DayOfWeek, List<TimeRange>> map) {
        List<DoctorWeeklyAvailabilityEntity> list = new ArrayList<>();
        if (map != null) {
            for (var entry : map.entrySet()) {
                for (var range : entry.getValue()) {
                    list.add(new DoctorWeeklyAvailabilityEntity(null, null, entry.getKey(), range.start(), range.end()));
                }
            }
        }
        return list;
    }

    default Map<DayOfWeek, List<TimeRange>> mapWeeklyList(List<DoctorWeeklyAvailabilityEntity> list) {
        Map<DayOfWeek, List<TimeRange>> map = new HashMap<>();
        if (list != null) {
            for (var item : list) {
                map.computeIfAbsent(item.getDayOfWeek(), d -> new ArrayList<>())
                   .add(new TimeRange(item.getStartTime(), item.getEndTime()));
            }
        }
        return map;
    }

    default List<DoctorSpecificAvailabilityEntity> mapSpecific(Map<LocalDate, List<TimeRange>> map) {
        List<DoctorSpecificAvailabilityEntity> list = new ArrayList<>();
        if (map != null) {
            for (var entry : map.entrySet()) {
                for (var range : entry.getValue()) {
                    list.add(new DoctorSpecificAvailabilityEntity(null, null, entry.getKey(), range.start(), range.end()));
                }
            }
        }
        return list;
    }

    default Map<LocalDate, List<TimeRange>> mapSpecificList(List<DoctorSpecificAvailabilityEntity> list) {
        Map<LocalDate, List<TimeRange>> map = new HashMap<>();
        if (list != null) {
            for (var item : list) {
                map.computeIfAbsent(item.getDate(), d -> new ArrayList<>())
                   .add(new TimeRange(item.getStartTime(), item.getEndTime()));
            }
        }
        return map;
    }
}
