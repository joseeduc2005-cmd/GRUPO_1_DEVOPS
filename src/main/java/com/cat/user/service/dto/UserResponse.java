package com.cat.user.service.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserResponse {

	UUID id;
	String nombre;
	String apellido;
	String direccion;
	String telefono;
	String correo;
}
