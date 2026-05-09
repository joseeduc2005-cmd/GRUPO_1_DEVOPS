package com.cat.user.service.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class UserRegistrationE2ETest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void register_validBody_returns201WithUuidJson() throws Exception {
		String json = """
				{"nombre":"Ana","apellido":"Pérez","direccion":"Calle 1","telefono":"3001234567","correo":"ana-%s@example.com"}
				""".formatted(UUID.randomUUID());

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value("Ana"))
				.andExpect(jsonPath("$.correo").exists()).andExpect(jsonPath("$.id").exists());
	}

	@Test
	void register_twoDistinctEmails_returnsDifferentIds() throws Exception {
		String email1 = "u1-" + UUID.randomUUID() + "@example.com";
		String email2 = "u2-" + UUID.randomUUID() + "@example.com";
		String body1 = """
				{"nombre":"A","apellido":"B","direccion":"C","telefono":"1","correo":"%s"}
				""".formatted(email1);
		String body2 = """
				{"nombre":"A","apellido":"B","direccion":"C","telefono":"1","correo":"%s"}
				""".formatted(email2);

		MvcResult r1 = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body1))
				.andExpect(status().isCreated()).andReturn();
		MvcResult r2 = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body2))
				.andExpect(status().isCreated()).andReturn();

		JsonNode n1 = objectMapper.readTree(r1.getResponse().getContentAsString());
		JsonNode n2 = objectMapper.readTree(r2.getResponse().getContentAsString());
		assertThat(n1.get("id").asText()).isNotEqualTo(n2.get("id").asText());
	}

	@Test
	void duplicateRegistration_returnsProblemDetail_andAllowsOtherRegistrations() throws Exception {
		String baseEmail = "dup-" + UUID.randomUUID() + "@example.com";
		String first = """
				{"nombre":"Ana","apellido":"Pérez","direccion":"Calle 1","telefono":"3001234567","correo":"%s"}
				""".formatted(baseEmail);

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(first))
				.andExpect(status().isCreated());

		String secondCase = """
				{"nombre":"Ana","apellido":"Pérez","direccion":"Calle 1","telefono":"3001234567","correo":"  %s  "}
				""".formatted(baseEmail.toUpperCase());

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(secondCase))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Usuario ya existente"));

		String third = """
				{"nombre":"Ana","apellido":"Pérez","direccion":"Calle 1","telefono":"3001234567","correo":"%s"}
				""".formatted(baseEmail);

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(third))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Usuario ya existente"));

		String other = """
				{"nombre":"Bob","apellido":"López","direccion":"Calle 2","telefono":"3007654321","correo":"other-%s@example.com"}
				""".formatted(UUID.randomUUID());

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(other))
				.andExpect(status().isCreated());
	}

	@Test
	void missingFields_returnsAllFieldErrorsInSpanish() throws Exception {
		String json = """
				{ "nombre": "Ana", "correo": "x@y.co" }
				""";

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Validación fallida"))
				.andExpect(jsonPath("$.errors.length()").value(3));
	}

	@Test
	void malformedJson_returnsSolicitudInvalida() throws Exception {
		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("not-json"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Solicitud inválida"))
				.andExpect(jsonPath("$.errors.length()").value(0));
	}

	@Test
	void invalidEmailFormat_returnsCorreoValidationMessage() throws Exception {
		String json = """
				{"nombre":"Ana","apellido":"Pérez","direccion":"Calle","telefono":"1","correo":"not-an-email"}
				""";

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("correo"))
				.andExpect(jsonPath("$.errors[0].message").value("el correo no tiene un formato valido"));
	}
}
