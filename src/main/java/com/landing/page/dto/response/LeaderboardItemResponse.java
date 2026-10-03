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
public class LeaderboardItemResponse {
    private Integer rank;
    private Long unitId;
    private String unitCode;
    private String unitName;
    private Integer totalPersonnel;
    private Integer validParticipantsCount;
    private Double participationRatePercent;
    private LocalDateTime lastUpdatedAt;
}
