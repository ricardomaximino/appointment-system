package es.brasatech.medpulse.adapters.out.persistence.repository;

import es.brasatech.medpulse.adapters.out.persistence.entity.UserEntity;
import es.brasatech.medpulse.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserEntityRepository extends JpaRepository<UserEntity, String> {

    @Query("SELECT u FROM UserEntity u JOIN u.roles r WHERE r = :role")
    List<UserEntity> findByRole(@Param("role") Role role);
}
