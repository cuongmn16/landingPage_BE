package com.landing.page.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionRequest {

    @NotBlank(message = "Mission code is required")
    private String code;

    @NotBlank(message = "Mission title is required")
    private String title;

    private String description;

    // Wrapper type: Jackson 3 rejects a missing/null value for primitives. null = open (default)
    private Boolean isOpen;
}
