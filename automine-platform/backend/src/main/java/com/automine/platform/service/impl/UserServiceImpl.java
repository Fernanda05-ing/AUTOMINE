package com.automine.platform.service.impl;

import com.automine.platform.dto.user.CreateUserRequest;
import com.automine.platform.dto.user.UserResponse;
import com.automine.platform.entity.Role;
import com.automine.platform.entity.User;
import com.automine.platform.entity.enums.UserStatus;
import com.automine.platform.exception.ApiException;
import com.automine.platform.repository.RoleRepository;
import com.automine.platform.repository.UserRepository;
import com.automine.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse create(CreateUserRequest request) {
        userRepository.findByUsernameAndDeletedAtIsNull(request.getUsername())
            .ifPresent(existing -> { throw new ApiException("El usuario ya existe"); });
        userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
            .ifPresent(existing -> { throw new ApiException("El correo ya existe"); });

        Role role = roleRepository.findByCodeAndDeletedAtIsNull(request.getRoleCode())
            .orElseThrow(() -> new ApiException("Rol no encontrado"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);

        return toResponse(userRepository.save(user));
    }

    @Override
    public List<UserResponse> listActive() {
        return userRepository.findByDeletedAtIsNull().stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public void deactivate(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ApiException("Usuario no encontrado"));
        user.setStatus(UserStatus.INACTIVE);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .role(user.getRole().getCode())
            .status(user.getStatus().name())
            .emailVerified(user.isEmailVerified())
            .build();
    }
}
