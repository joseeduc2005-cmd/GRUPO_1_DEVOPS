package com.cat.user.service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cat.user.service.domain.User;
import com.cat.user.service.dto.UserMapper;
import com.cat.user.service.dto.UserRequest;
import com.cat.user.service.dto.UserResponse;
import com.cat.user.service.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

	private final UserRepository userRepository;

	@Override
	public UserResponse register(UserRequest request) {
		UUID id = UUID.randomUUID();
		User user = UserMapper.toDomain(request, id);
		User saved = userRepository.save(user);
		return UserMapper.toResponse(saved);
	}
}
