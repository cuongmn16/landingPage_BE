package com.landing.page.service;

import com.landing.page.dto.request.RuleConfigRequest;
import com.landing.page.entity.SystemRuleConfig;
import com.landing.page.entity.enums.RuleType;
import com.landing.page.repository.SystemRuleConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RuleConfigService {

    public static final String KEY_MIN_MISSIONS = "VALID_PARTICIPANT_MIN_MISSIONS";

    private final SystemRuleConfigRepository ruleConfigRepository;

    @Transactional(readOnly = true)
    public SystemRuleConfig getValidParticipantRule() {
        return ruleConfigRepository.findByConfigKey(KEY_MIN_MISSIONS)
                .orElseGet(() -> SystemRuleConfig.builder()
                        .configKey(KEY_MIN_MISSIONS)
                        .ruleType(RuleType.MIN_COMPLETED_MISSIONS)
                        .configValue("1") // Default: At least 1 valid submission
                        .description("Số bài nộp hợp lệ tối thiểu để nhân sự được tính là tham gia hợp lệ")
                        .build());
    }

    @Transactional
    public SystemRuleConfig updateValidParticipantRule(RuleConfigRequest request) {
        SystemRuleConfig config = ruleConfigRepository.findByConfigKey(KEY_MIN_MISSIONS)
                .orElseGet(() -> SystemRuleConfig.builder()
                        .configKey(KEY_MIN_MISSIONS)
                        .build());

        config.setRuleType(request.getRuleType());
        config.setConfigValue(request.getConfigValue());
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        }
        return ruleConfigRepository.save(config);
    }
}
