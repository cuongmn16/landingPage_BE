package com.landing.page.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndividualLeaderboardItemResponse {
    private Integer rank;
    private Long employeeId;
    private String fullName;
    private String unitCode;
    private String unitName;
    private Double totalScore;
    private LocalDateTime lastUpdatedAt;
}
