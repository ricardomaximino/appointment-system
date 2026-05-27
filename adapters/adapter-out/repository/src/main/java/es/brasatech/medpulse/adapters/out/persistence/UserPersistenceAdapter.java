package es.brasatech.medpulse.adapters.out.persistence;

import es.brasatech.medpulse.application.port.out.UserPersistencePort;
import es.brasatech.medpulse.adapters.out.persistence.entity.UserEntity;
import es.brasatech.medpulse.adapters.out.persistence.repository.UserEntityRepository;
import es.brasatech.medpulse.adapters.out.persistence.mapper.DomainMapper;
import es.brasatech.medpulse.domain.User;
import es.brasatech.medpulse.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserPersistencePort {

    private final UserEntityRepository userEntityRepository;
    private final DomainMapper mapper;

    @Override
    public User save(User user) {
        UserEntity entity = mapper.toEntity(user);
        UserEntity saved = userEntityRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public void deleteById(String id) {
        userEntityRepository.deleteById(id);
    }

    @Override
    public Optional<User> findById(String id) {
        return userEntityRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<User> findAll() {
        return userEntityRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<User> findByRole(Role role) {
        return userEntityRepository.findByRole(role).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
