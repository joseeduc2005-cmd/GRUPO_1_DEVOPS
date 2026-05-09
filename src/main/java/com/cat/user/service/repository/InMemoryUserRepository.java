package com.cat.user.service.repository;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.cat.user.service.domain.User;
import com.cat.user.service.exceptions.DuplicateUserException;

@Repository
public class InMemoryUserRepository implements UserRepository {

	private final ConcurrentHashMap<UUID, User> usersById = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, UUID> idByEmailKey = new ConcurrentHashMap<>();

	@Override
	public User save(User user) {
		String emailKey = user.getCorreo().trim().toLowerCase(Locale.ROOT);
		UUID existingOwner = idByEmailKey.putIfAbsent(emailKey, user.getId());
		if (existingOwner != null && !existingOwner.equals(user.getId())) {
			throw new DuplicateUserException(user.getCorreo());
		}
		usersById.put(user.getId(), user);
		return user;
	}

	@Override
	public Optional<User> findById(UUID id) {
		return Optional.ofNullable(usersById.get(id));
	}
}
