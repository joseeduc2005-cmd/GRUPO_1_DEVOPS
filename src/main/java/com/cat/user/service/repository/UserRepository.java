package com.cat.user.service.repository;

import java.util.Optional;
import java.util.UUID;

import com.cat.user.service.domain.User;

public interface UserRepository {

	User save(User user);

	Optional<User> findById(UUID id);
}
