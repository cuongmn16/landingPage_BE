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
    /** Tổng số mission có trong hệ thống (mỗi mission nộp hợp lệ = 1 điểm). */
    private Integer totalMissions;
    /** Điểm đơn vị đạt được = tổng số mission đã nộp hợp lệ của mọi thành viên. */
    private Long earnedPoints;
    /** Điểm tối đa = số nhân sự × số mission. */
    private Long maxPoints;
    /** Tỷ lệ điểm (%) = earnedPoints / maxPoints × 100 — tiêu chí xếp hạng. */
    private Double scorePercent;
    private LocalDateTime lastUpdatedAt;
}
