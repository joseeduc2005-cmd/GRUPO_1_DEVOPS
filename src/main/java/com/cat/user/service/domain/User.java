package com.cat.user.service.domain;

import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class User {

	UUID id;
	String nombre;
	String apellido;
	String direccion;
	String telefono;
	String correo;
}
