package com.landing.page.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GratitudeLetterResponse {
    private String email;
    private String employeeCode;
    private String fullName;
    private String salutation;
    private String unitName;
    private Integer daysOfService;
    private String yearsDesc;
    private Boolean isOpened;
}
