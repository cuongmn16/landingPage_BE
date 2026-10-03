package com.landing.page.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionRequest {

    private Long employeeId; // Optional: Extracted from JWT Token if omitted

    @NotNull(message = "Mission ID is required")
    private Long missionId;

    private String longTextAnswer;
    private String selectedOptionsJson;
    private String orderedDataJson;
    private String evidenceSource;
    private String witnessName;
}
