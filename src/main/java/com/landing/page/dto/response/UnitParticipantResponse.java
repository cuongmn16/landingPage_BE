package com.landing.page.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitParticipantResponse {
    private Long employeeId;
    private String fullName;
    private Long validSubmissions;
}
