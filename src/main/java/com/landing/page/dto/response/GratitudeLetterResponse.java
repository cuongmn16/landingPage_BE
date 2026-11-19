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
public class GratitudeLetterResponse {
    private Long id;
    private String email;
    private String employeeCode;
    private String fullName;
    private String salutation;
    private String unitName;
    private Integer daysOfService;
    private String yearsDesc;
    private Boolean isOpened;
    private LocalDateTime openedAt;
    private Boolean isPublished;
}
