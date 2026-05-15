package com.cat.user.service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataUserRepository extends JpaRepository<UserEntity, UUID> {

	Optional<UserEntity> findByCorreoIgnoreCase(String correo);
}
