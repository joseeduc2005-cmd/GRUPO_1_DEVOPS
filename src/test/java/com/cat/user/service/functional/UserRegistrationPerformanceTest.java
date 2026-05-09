package com.cat.user.service.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("performance")
class UserRegistrationPerformanceTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void sequentialRegistrations_p95Below200Ms() throws Exception {
		List<Long> durationsMs = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			String json = """
					{"nombre":"P","apellido":"F","direccion":"D","telefono":"1","correo":"perf-%s@example.com"}
					""".formatted(UUID.randomUUID());
			long start = System.nanoTime();
			mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json))
					.andExpect(status().isCreated());
			durationsMs.add((System.nanoTime() - start) / 1_000_000L);
		}
		Collections.sort(durationsMs);
		int idx = (int) Math.ceil(0.95 * durationsMs.size()) - 1;
		long p95 = durationsMs.get(Math.max(0, idx));
		assertThat(p95).isLessThan(200L);
	}
}
