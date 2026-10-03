package com.landing.page.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitRequest {

    @NotBlank(message = "Unit code is required")
    private String code;

    @NotBlank(message = "Unit name is required")
    private String name;

    @NotNull(message = "Total personnel is required")
    @Min(value = 1, message = "Total personnel must be at least 1")
    private Integer totalPersonnel;
}
