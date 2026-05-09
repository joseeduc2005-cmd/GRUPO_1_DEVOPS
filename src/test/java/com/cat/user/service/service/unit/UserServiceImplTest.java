package com.cat.user.service.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cat.user.service.domain.User;
import com.cat.user.service.dto.UserRequest;
import com.cat.user.service.dto.UserResponse;
import com.cat.user.service.exceptions.DuplicateUserException;
import com.cat.user.service.repository.UserRepository;
import com.cat.user.service.service.UserServiceImpl;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserServiceImpl userService;

	private UserRequest validRequest;

	@BeforeEach
	void setUp() {
		validRequest = new UserRequest(" Ana ", " Pérez ", " Calle 1 ", " 3001234567 ", " ana@example.com ");
	}

	@Test
	void register_happyPath_returnsResponseWithUuidAndTrimmedFields() {
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = userService.register(validRequest);

		assertThat(response.getId()).isNotNull();
		assertThat(response.getNombre()).isEqualTo("Ana");
		assertThat(response.getApellido()).isEqualTo("Pérez");
		assertThat(response.getDireccion()).isEqualTo("Calle 1");
		assertThat(response.getTelefono()).isEqualTo("3001234567");
		assertThat(response.getCorreo()).isEqualTo("ana@example.com");
		verify(userRepository).save(any(User.class));
	}

	@Test
	void register_whenRepositoryThrows_propagatesException() {
		when(userRepository.save(any(User.class))).thenThrow(new IllegalStateException("store failure"));

		assertThatThrownBy(() -> userService.register(validRequest)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("store failure");
	}

	@Test
	void register_whenRepositoryThrowsDuplicate_propagatesDuplicateUserException() {
		when(userRepository.save(any(User.class))).thenThrow(new DuplicateUserException("ana@example.com"));

		assertThatThrownBy(() -> userService.register(validRequest)).isInstanceOf(DuplicateUserException.class)
				.extracting(ex -> ((DuplicateUserException) ex).getCorreo()).isEqualTo("ana@example.com");
	}
}
