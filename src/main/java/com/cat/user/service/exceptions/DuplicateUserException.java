package com.cat.user.service.exceptions;

public class DuplicateUserException extends RuntimeException {

	private final String correo;

	public DuplicateUserException(String correo) {
		super("Duplicate registration for correo: " + correo);
		this.correo = correo;
	}

	public String getCorreo() {
		return correo;
	}
}
