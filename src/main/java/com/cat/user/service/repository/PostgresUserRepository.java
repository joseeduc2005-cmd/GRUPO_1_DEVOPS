package com.cat.user.service.repository;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.cat.user.service.domain.User;
import com.cat.user.service.exceptions.DuplicateUserException;

import lombok.RequiredArgsConstructor;

@Repository
@Profile("!test")
@RequiredArgsConstructor
public class PostgresUserRepository implements UserRepository {

	private final SpringDataUserRepository springDataUserRepository;

	@Override
	public User save(User user) {
		String correo = user.getCorreo().trim();
		springDataUserRepository.findByCorreoIgnoreCase(correo.toLowerCase(Locale.ROOT))
				.filter(existing -> !existing.getId().equals(user.getId()))
				.ifPresent(existing -> {
					throw new DuplicateUserException(user.getCorreo());
				});

		UserEntity entity = new UserEntity();
		entity.setId(user.getId());
		entity.setNombre(user.getNombre());
		entity.setApellido(user.getApellido());
		entity.setDireccion(user.getDireccion());
		entity.setTelefono(user.getTelefono());
		entity.setCorreo(correo);
		return toDomain(springDataUserRepository.save(entity));
	}

	@Override
	public Optional<User> findById(UUID id) {
		return springDataUserRepository.findById(id).map(this::toDomain);
	}

	private User toDomain(UserEntity entity) {
		return User.builder()
				.id(entity.getId())
				.nombre(entity.getNombre())
				.apellido(entity.getApellido())
				.direccion(entity.getDireccion())
				.telefono(entity.getTelefono())
				.correo(entity.getCorreo())
				.build();
	}
}
