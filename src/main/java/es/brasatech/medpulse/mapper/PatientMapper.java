package es.brasatech.medpulse.mapper;

import es.brasatech.medpulse.domain.Patient;
import es.brasatech.medpulse.entity.PatientEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface PatientMapper {

    PatientMapper INSTANCE = Mappers.getMapper(PatientMapper.class);

    PatientEntity toEntity(Patient patient);

    Patient toDomain(PatientEntity entity);
}
