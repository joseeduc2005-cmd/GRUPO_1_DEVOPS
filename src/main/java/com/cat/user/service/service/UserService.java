package com.cat.user.service.service;

import com.cat.user.service.dto.UserRequest;
import com.cat.user.service.dto.UserResponse;

public interface UserService {

	UserResponse register(UserRequest request);
}
