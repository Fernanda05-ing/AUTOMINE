package com.automine.platform.dto.user;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserResponse {
    Integer id;
    String username;
    String email;
    String role;
    String status;
    boolean emailVerified;
}
