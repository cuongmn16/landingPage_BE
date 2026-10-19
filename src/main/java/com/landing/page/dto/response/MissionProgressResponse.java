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
public class MissionProgressResponse {
    private Long missionId;
    private String progressJson;
    private LocalDateTime updatedAt;
}
