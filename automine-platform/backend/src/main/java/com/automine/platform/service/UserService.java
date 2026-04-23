package com.automine.platform.service;

import com.automine.platform.dto.user.CreateUserRequest;
import com.automine.platform.dto.user.UserResponse;

import java.util.List;

public interface UserService {
    UserResponse create(CreateUserRequest request);
    List<UserResponse> listActive();
    void deactivate(Long id);
}
