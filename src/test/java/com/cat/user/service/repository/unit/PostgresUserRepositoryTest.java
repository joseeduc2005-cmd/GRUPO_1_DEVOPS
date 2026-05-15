package com.cat.user.service.repository.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cat.user.service.domain.User;
import com.cat.user.service.exceptions.DuplicateUserException;
import com.cat.user.service.repository.PostgresUserRepository;
import com.cat.user.service.repository.SpringDataUserRepository;
import com.cat.user.service.repository.UserEntity;

@ExtendWith(MockitoExtension.class)
class PostgresUserRepositoryTest {

	@Mock
	private SpringDataUserRepository springDataUserRepository;

	@InjectMocks
	private PostgresUserRepository repository;

	@Test
	void save_newUser_persistsAndReturnsDomainUser() {
		UUID id = UUID.randomUUID();
		when(springDataUserRepository.findByCorreoIgnoreCase(any())).thenReturn(Optional.empty());
		when(springDataUserRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		User saved = repository.save(baseUser(id, "ana@example.com"));

		assertThat(saved.getId()).isEqualTo(id);
		assertThat(saved.getCorreo()).isEqualTo("ana@example.com");
	}

	@Test
	void save_existingEmail_throwsDuplicateUserException() {
		UserEntity existing = new UserEntity();
		existing.setId(UUID.randomUUID());
		existing.setCorreo("ana@example.com");
		when(springDataUserRepository.findByCorreoIgnoreCase(any())).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> repository.save(baseUser(UUID.randomUUID(), "  ANA@EXAMPLE.COM  ")))
				.isInstanceOf(DuplicateUserException.class);
	}

	@Test
	void findById_whenPresent_returnsDomainUser() {
		UUID id = UUID.randomUUID();
		UserEntity entity = new UserEntity();
		entity.setId(id);
		entity.setNombre("Ana");
		entity.setApellido("Pérez");
		entity.setDireccion("Calle 1");
		entity.setTelefono("3001234567");
		entity.setCorreo("ana@example.com");
		when(springDataUserRepository.findById(id)).thenReturn(Optional.of(entity));

		assertThat(repository.findById(id)).map(User::getNombre).contains("Ana");
	}

	private static User baseUser(UUID id, String correo) {
		return User.builder()
				.id(id)
				.nombre("N")
				.apellido("A")
				.direccion("D")
				.telefono("1")
				.correo(correo)
				.build();
	}
}
