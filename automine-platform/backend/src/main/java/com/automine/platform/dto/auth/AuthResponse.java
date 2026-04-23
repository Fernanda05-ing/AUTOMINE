package com.automine.platform.dto.auth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthResponse {
    String accessToken;
    String tokenType;
    Long expiresIn;
    String username;
    String role;
}
