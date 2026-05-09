package com.cat.user.service.exceptions;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(DuplicateUserException.class)
	public ResponseEntity<ProblemDetail> handleDuplicateUser(DuplicateUserException ex, HttpServletRequest request) {
		log.warn("Intento de registro duplicado para correo={}", ex.getCorreo());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"El usuario con el correo proporcionado ya se encuentra registrado.");
		problem.setTitle("Usuario ya existente");
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("errors", List.of(Map.of("field", "correo", "message", "el correo ya está registrado")));
		return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
			@NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
		LinkedHashMap<String, Map<String, String>> orderedUnique = new LinkedHashMap<>();
		for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
			String message = fe.getDefaultMessage() == null ? "" : fe.getDefaultMessage();
			String dedupeKey = fe.getField() + "|" + message;
			orderedUnique.putIfAbsent(dedupeKey, Map.of("field", fe.getField(), "message", message));
		}
		List<Map<String, String>> errors = new ArrayList<>(orderedUnique.values());

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Uno o más campos requeridos no fueron enviados o están vacíos.");
		problem.setTitle("Validación fallida");
		String uri = ((ServletWebRequest) request).getRequest().getRequestURI();
		problem.setInstance(URI.create(uri == null ? "/" : uri));
		problem.setProperty("errors", errors);

		return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException ex,
			@NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"El cuerpo de la petición no es un JSON válido.");
		problem.setTitle("Solicitud inválida");
		String uri = ((ServletWebRequest) request).getRequest().getRequestURI();
		problem.setInstance(URI.create(uri == null ? "/" : uri));
		problem.setProperty("errors", List.of());
		log.warn("Cuerpo JSON inválido: {}", ex.getMessage());
		return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
	}
}
