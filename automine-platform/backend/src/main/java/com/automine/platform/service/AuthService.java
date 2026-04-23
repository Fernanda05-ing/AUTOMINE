package com.automine.platform.service;

import com.automine.platform.dto.auth.AuthRequest;
import com.automine.platform.dto.auth.AuthResponse;

public interface AuthService {
    AuthResponse login(AuthRequest request);
    void forgotPassword(String email);
}
