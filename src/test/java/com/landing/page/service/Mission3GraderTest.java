package com.landing.page.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mission3GraderTest {

    private static final String REFLECTION =
            "Tôi nghĩ người CTIN hôm nay cần tiếp tục gìn giữ tinh thần dám chịu trách nhiệm, dám mở đường và luôn học hỏi đổi mới mỗi ngày.";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private Mission3Grader grader;

    @BeforeEach
    void setUp() {
        grader = new Mission3Grader(mapper);
        ReflectionTestUtils.setField(grader, "allowDraftContent", true);
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("missionId", "M3");
        p.put("agreed", true);
        p.put("viewedLeaders", List.of("L01", "L02", "L03", "L04", "L05"));
        p.put("markMatches", new LinkedHashMap<>(Map.of("L01", "MK-R4", "L02", "MK-T7", "L03", "MK-B2", "L04", "MK-J9", "L05", "MK-F3")));
        p.put("qualityMatches", new LinkedHashMap<>(Map.of("L01", "QL-H5", "L02", "QL-C8", "L03", "QL-W1", "L04", "QL-N6", "L05", "QL-D2")));
        p.put("continuationSelections", new LinkedHashMap<>(Map.of("L01", "C01", "L02", "C01", "L03", "C03", "L04", "C04", "L05", "C05")));
        p.put("leadershipReflection", REFLECTION);
        return p;
    }

    private String json(Object o) {
        return mapper.writeValueAsString(o);
    }

    @Test
    void validSubmissionScores100AndIsNormalized() {
        Map<String, Object> p = validPayload();
        // Trường của prototype cũ phải bị loại khỏi payload lưu trữ
        p.put("contextMatches", Map.of("L01", "X"));
        p.put("quote", "q");
        p.put("story", "s");
        p.put("evidence", "e");
        p.put("score", 0);
        p.put("isValid", false);

        Mission3Grader.Result r = grader.grade(json(p));
        JsonNode out = mapper.readTree(r.answersJson());

        assertThat(r.score()).isEqualTo(100);
        assertThat(out.path("score").asInt()).isEqualTo(100);
        assertThat(out.path("isValid").asBoolean()).isTrue();
        assertThat(out.path("contentVersion").asString()).isEqualTo(Mission3Grader.CONTENT_VERSION);
        assertThat(out.has("contextMatches")).isFalse();
        assertThat(out.has("quote")).isFalse();
        assertThat(out.has("story")).isFalse();
        assertThat(out.has("evidence")).isFalse();
        assertThat(r.summaryLine()).contains("100/100");
    }

    @Test
    void rejectsMissingAgreement() {
        Map<String, Object> p = validPayload();
        p.put("agreed", false);
        assertThatThrownBy(() -> grader.grade(json(p))).hasMessageContaining("xác nhận");
    }

    @Test
    void rejectsWhenNotAllProfilesViewed() {
        Map<String, Object> p = validPayload();
        p.put("viewedLeaders", List.of("L01", "L02", "L03", "L04", "L04", "L99"));
        assertThatThrownBy(() -> grader.grade(json(p))).hasMessage("Hãy mở đủ năm hồ sơ lãnh đạo.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsIncompleteMatches() {
        Map<String, Object> p = validPayload();
        ((Map<String, Object>) p.get("qualityMatches")).remove("L03");
        assertThatThrownBy(() -> grader.grade(json(p))).hasMessage("Hãy hoàn thành đủ hai lựa chọn cho mỗi người dẫn đường.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsWrongMatchesEvenIfClientClaimsValid() {
        Map<String, Object> p = validPayload();
        Map<String, Object> marks = (Map<String, Object>) p.get("markMatches");
        marks.put("L01", "MK-T7");
        marks.put("L02", "MK-R4");
        p.put("isValid", true);
        p.put("score", 100);
        assertThatThrownBy(() -> grader.grade(json(p))).hasMessage("Một số kết nối chưa chính xác. Hãy xem lại hồ sơ và thử lại.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsMissingOrUnknownContinuation() {
        Map<String, Object> p = validPayload();
        ((Map<String, Object>) p.get("continuationSelections")).put("L05", "C99");
        assertThatThrownBy(() -> grader.grade(json(p))).hasMessage("Hãy lựa chọn điều cần tiếp nối từ cả năm người dẫn đường.");
    }

    @Test
    void rejectsShortReflectionAfterTrim() {
        Map<String, Object> p = validPayload();
        p.put("leadershipReflection", "   " + "a".repeat(99) + "      ");
        assertThatThrownBy(() -> grader.grade(json(p))).hasMessage("Phần chia sẻ cần có ít nhất 100 ký tự.");
    }

    @Test
    void repeatedCharacterReflectionIsValidButLosesTenPoints() {
        Map<String, Object> p = validPayload();
        p.put("leadershipReflection", "a".repeat(120));
        assertThat(grader.grade(json(p)).score()).isEqualTo(90);
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> grader.grade("{không phải json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> grader.grade(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> grader.grade("[1,2]")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsSubmissionInProductionModeWhenAllContentApproved() {
        ReflectionTestUtils.setField(grader, "allowDraftContent", false);
        assertThat(grader.grade(json(validPayload())).score()).isEqualTo(100);
    }
}
