package com.landing.page.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidParticipantResponse {
    private Long employeeId;
    private String employeeCode;
    private String fullName;
    private String email;
    private String unitName;
    private boolean isEmailVerified;
    private long totalValidSubmissions;
    private long totalRequiredMissions;
    private boolean isValidParticipant;
}
