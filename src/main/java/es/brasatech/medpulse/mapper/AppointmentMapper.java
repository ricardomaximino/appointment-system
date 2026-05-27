package es.brasatech.medpulse.mapper;

import es.brasatech.medpulse.domain.Appointment;
import es.brasatech.medpulse.domain.AppointmentSlot;
import es.brasatech.medpulse.domain.Patient;
import es.brasatech.medpulse.entity.AppointmentEntity;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", uses = {DoctorMapper.class, PatientMapper.class})
public interface AppointmentMapper {

    AppointmentMapper INSTANCE = Mappers.getMapper(AppointmentMapper.class);

    @Mapping(target = "doctor", source = "slot.doctor")
    @Mapping(target = "dateTime", source = "slot.dateTime")
    @Mapping(target = "type", source = "slot.type")
    @Mapping(target = "patient", source = "patient")
    @Mapping(target = "id", ignore = true)
    AppointmentEntity toEntity(AppointmentSlot slot, Patient patient);

    @Mapping(target = "doctor", source = "doctor")
    @Mapping(target = "patient", source = "patient")
    @Mapping(target = "dateTime", source = "dateTime")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "id", ignore = true)
    AppointmentEntity toEntity(Appointment appointment);

    Appointment toDomain(AppointmentEntity entity);
}
