package com.landing.page.controller;

import com.landing.page.dto.request.RuleConfigRequest;
import com.landing.page.dto.response.ApiResponse;
import com.landing.page.entity.SystemRuleConfig;
import com.landing.page.service.RuleConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rule-config")
@RequiredArgsConstructor
public class RuleConfigController {

    private final RuleConfigService ruleConfigService;

    @GetMapping("/valid-participant")
    public ResponseEntity<ApiResponse<SystemRuleConfig>> getValidParticipantRule() {
        SystemRuleConfig rule = ruleConfigService.getValidParticipantRule();
        return ResponseEntity.ok(ApiResponse.ok(rule));
    }

    @PutMapping("/valid-participant")
    public ResponseEntity<ApiResponse<SystemRuleConfig>> updateValidParticipantRule(@Valid @RequestBody RuleConfigRequest request) {
        SystemRuleConfig rule = ruleConfigService.updateValidParticipantRule(request);
        return ResponseEntity.ok(ApiResponse.ok("Valid participant rule updated by BTC", rule));
    }
}
