package com.automine.platform.dto.user;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserResponse {
    Long id;
    String username;
    String email;
    String role;
    String status;
    boolean emailVerified;
}
