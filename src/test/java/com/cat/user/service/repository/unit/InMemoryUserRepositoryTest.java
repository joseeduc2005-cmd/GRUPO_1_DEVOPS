package com.cat.user.service.repository.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cat.user.service.domain.User;
import com.cat.user.service.exceptions.DuplicateUserException;
import com.cat.user.service.repository.InMemoryUserRepository;

class InMemoryUserRepositoryTest {

	private InMemoryUserRepository repository;

	@BeforeEach
	void setUp() {
		repository = new InMemoryUserRepository();
	}

	@Test
	void save_then_findById_returnsSameUser() {
		UUID id = UUID.randomUUID();
		User user = baseUser(id, "ana@example.com");
		User saved = repository.save(user);

		assertThat(repository.findById(id)).contains(saved);
	}

	@Test
	void secondSave_withCaseDifferentEmail_throwsDuplicateUserException() {
		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();
		repository.save(baseUser(id1, "ana@example.com"));

		assertThatThrownBy(() -> repository.save(baseUser(id2, "  ANA@EXAMPLE.COM  ")))
				.isInstanceOf(DuplicateUserException.class);
	}

	@Test
	void concurrentSaves_sameEmail_onlyOneStored() throws Exception {
		CyclicBarrier barrier = new CyclicBarrier(2);
		AtomicInteger successes = new AtomicInteger();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<?> f1 = pool.submit(() -> saveAfterBarrier(barrier, successes, "race@test.com"));
			Future<?> f2 = pool.submit(() -> saveAfterBarrier(barrier, successes, "race@test.com"));
			f1.get(5, TimeUnit.SECONDS);
			f2.get(5, TimeUnit.SECONDS);
		}
		finally {
			pool.shutdown();
			assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		}

		assertThat(successes.get()).isEqualTo(1);
	}

	private void saveAfterBarrier(CyclicBarrier barrier, AtomicInteger successes, String email) {
		try {
			barrier.await(5, TimeUnit.SECONDS);
			User u = baseUser(UUID.randomUUID(), email);
			try {
				repository.save(u);
				successes.incrementAndGet();
			}
			catch (DuplicateUserException ignored) {
				// expected for losing thread
			}
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static User baseUser(UUID id, String correo) {
		return User.builder()
				.id(id)
				.nombre("N")
				.apellido("A")
				.direccion("D")
				.telefono("1")
				.correo(correo)
				.build();
	}
}
