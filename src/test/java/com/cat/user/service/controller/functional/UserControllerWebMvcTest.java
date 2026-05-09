package com.cat.user.service.controller.functional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cat.user.service.controller.UserController;
import com.cat.user.service.dto.UserRequest;
import com.cat.user.service.dto.UserResponse;
import com.cat.user.service.exceptions.ApiExceptionHandler;
import com.cat.user.service.exceptions.DuplicateUserException;
import com.cat.user.service.service.UserService;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = UserController.class)
@Import(ApiExceptionHandler.class)
class UserControllerWebMvcTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private UserService userService;

	@Test
	void postUsers_validBody_returns201AndEchoPayload() throws Exception {
		UUID id = UUID.randomUUID();
		when(userService.register(any(UserRequest.class))).thenReturn(UserResponse.builder().id(id).nombre("Ana")
				.apellido("Pérez").direccion("Calle 1").telefono("3001234567").correo("ana@example.com").build());

		UserRequest body = new UserRequest("Ana", "Pérez", "Calle 1", "3001234567", "ana@example.com");

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body))).andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(id.toString())).andExpect(jsonPath("$.nombre").value("Ana"))
				.andExpect(jsonPath("$.correo").value("ana@example.com"));
	}

	@Test
	void postUsers_duplicateEmail_returnsProblemDetail() throws Exception {
		when(userService.register(any(UserRequest.class))).thenThrow(new DuplicateUserException("ana@example.com"));

		UserRequest body = new UserRequest("Ana", "Pérez", "Calle 1", "3001234567", "ana@example.com");

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body))).andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Usuario ya existente")).andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors[0].field").value("correo"))
				.andExpect(jsonPath("$.errors[0].message").value("el correo ya está registrado"));
	}

	@Test
	void postUsers_missingOneField_listsThatField() throws Exception {
		String json = """
				{"apellido":"Pérez","direccion":"Calle","telefono":"1","correo":"a@b.co"}
				""";

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Validación fallida"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("nombre")));
	}

	@Test
	void postUsers_missingThreeFields_listsAllThree() throws Exception {
		String json = """
				{"nombre":"Ana","correo":"x@y.co"}
				""";

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.length()").value(3))
				.andExpect(jsonPath("$.errors[*].field", hasItem("apellido")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("direccion")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("telefono")));
	}

	@Test
	void postUsers_whitespaceOnlyNombre_treatedAsMissing() throws Exception {
		String json = """
				{"nombre":"   ","apellido":"Pérez","direccion":"Calle","telefono":"1","correo":"a@b.co"}
				""";

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("nombre")));
	}
}
