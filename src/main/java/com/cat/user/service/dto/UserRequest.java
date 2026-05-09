package com.cat.user.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Setters trim whitespace so Bean Validation and duplicate-email checks operate on the same
 * normalized surface values as {@link UserMapper} (FR trim semantics, padded-email duplicates).
 */
@Getter
@NoArgsConstructor
public class UserRequest {

	@NotBlank(message = "el campo nombre es obligatorio")
	private String nombre;

	@NotBlank(message = "el campo apellido es obligatorio")
	private String apellido;

	@NotBlank(message = "el campo direccion es obligatorio")
	private String direccion;

	@NotBlank(message = "el campo telefono es obligatorio")
	private String telefono;

	@NotBlank(message = "el campo correo es obligatorio")
	@Email(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "el correo no tiene un formato valido")
	private String correo;

	public UserRequest(String nombre, String apellido, String direccion, String telefono, String correo) {
		setNombre(nombre);
		setApellido(apellido);
		setDireccion(direccion);
		setTelefono(telefono);
		setCorreo(correo);
	}

	public void setNombre(String nombre) {
		this.nombre = nombre == null ? null : nombre.trim();
	}

	public void setApellido(String apellido) {
		this.apellido = apellido == null ? null : apellido.trim();
	}

	public void setDireccion(String direccion) {
		this.direccion = direccion == null ? null : direccion.trim();
	}

	public void setTelefono(String telefono) {
		this.telefono = telefono == null ? null : telefono.trim();
	}

	public void setCorreo(String correo) {
		this.correo = correo == null ? null : correo.trim();
	}
}
