package com.landing.page.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GratitudeLetterAdminSummaryResponse {
    private Boolean isPublished;
    private String publishMode;
    private Long total;
    private Long openedCount;
    private List<GratitudeLetterResponse> letters;
}
