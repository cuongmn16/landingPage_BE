package com.landing.page.dto.request;

import com.landing.page.entity.enums.RuleType;
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
public class RuleConfigRequest {

    @NotNull(message = "Rule type is required")
    private RuleType ruleType;

    @NotBlank(message = "Config value is required")
    private String configValue;

    private String description;
}
