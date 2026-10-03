package com.landing.page.dto.response;

import com.landing.page.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String token; // Alias for accessToken for backward compatibility
    @Builder.Default
    private String tokenType = "Bearer";
    private Long expiresIn;

    private Long employeeId;
    private String employeeCode;
    private String email;
    private String fullName;
    private Role role;
    private String unitCode;
    private String unitName;
}
