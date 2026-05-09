package com.cat.user.service.dto;

import java.util.UUID;

import com.cat.user.service.domain.User;

public final class UserMapper {

	private UserMapper() {
	}

	public static User toDomain(UserRequest req, UUID id) {
		return User.builder()
				.id(id)
				.nombre(req.getNombre().trim())
				.apellido(req.getApellido().trim())
				.direccion(req.getDireccion().trim())
				.telefono(req.getTelefono().trim())
				.correo(req.getCorreo().trim())
				.build();
	}

	public static UserResponse toResponse(User user) {
		return UserResponse.builder()
				.id(user.getId())
				.nombre(user.getNombre())
				.apellido(user.getApellido())
				.direccion(user.getDireccion())
				.telefono(user.getTelefono())
				.correo(user.getCorreo())
				.build();
	}
}
