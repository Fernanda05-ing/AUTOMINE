package com.automine.platform.service.impl;

import com.automine.platform.dto.auth.AuthRequest;
import com.automine.platform.dto.auth.AuthResponse;
import com.automine.platform.entity.User;
import com.automine.platform.exception.ApiException;
import com.automine.platform.repository.UserRepository;
import com.automine.platform.security.JwtService;
import com.automine.platform.service.AuthService;
import com.automine.platform.utils.MailUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final MailUtils mailUtils;

    @Value("${app.jwt.access-token-minutes}")
    private long accessTokenMinutes;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsernameAndDeletedAtIsNull(request.getUsername())
            .orElseThrow(() -> new ApiException("Credenciales invalidas"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtService.generateAccessToken(userDetails, Map.of("role", user.getRole().getCode()));
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return AuthResponse.builder()
            .accessToken(token)
            .tokenType("Bearer")
            .expiresIn(accessTokenMinutes * 60)
            .username(user.getUsername())
            .role(user.getRole().getCode())
            .build();
    }

    @Override
    public void forgotPassword(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
            .orElseThrow(() -> new ApiException("No existe un usuario con ese correo"));

        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetExpiresAt(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        String link = frontendUrl + "/auth/reset-password?token=" + token;
        mailUtils.send(email, "AUTOMINE - Recuperacion de contrasena",
            "Hola " + user.getUsername() + ",\n\nRecupera tu contrasena desde este enlace:\n" + link +
                "\n\nEste enlace vence en 1 hora.");
    }
}
