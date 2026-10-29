package com.landing.page.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mission 3 — Leadership Legacies: backend là nguồn quyết định đáp án, điểm và trạng thái hợp lệ.
 * Đáp án chuẩn phải khớp với du_an/src/data/mission3Data.js (cùng CONTENT_VERSION).
 */
@Component
@RequiredArgsConstructor
public class Mission3Grader {

    public static final String MISSION_CODE = "M3";
    public static final String CONTENT_VERSION = "m3-2026.10";
    public static final int MIN_REFLECTION_CHARS = 100;

    /** leaderId → [markId, qualityId, contentStatus] */
    private static final Map<String, String[]> ANSWER_KEY = new LinkedHashMap<>();
    static {
        ANSWER_KEY.put("L01", new String[]{"MK-R4", "QL-H5", "approved"});
        ANSWER_KEY.put("L02", new String[]{"MK-T7", "QL-C8", "approved"});
        ANSWER_KEY.put("L03", new String[]{"MK-B2", "QL-W1", "approved"});
        ANSWER_KEY.put("L04", new String[]{"MK-J9", "QL-N6", "approved"});
        ANSWER_KEY.put("L05", new String[]{"MK-F3", "QL-D2", "approved"});
    }
    private static final Set<String> CONTINUATION_IDS = Set.of("C01", "C02", "C03", "C04", "C05");

    private final JsonMapper jsonMapper;

    /** Production phải đặt false: chặn nộp bài khi còn nội dung lãnh đạo chưa được BTC phê duyệt. */
    @Value("${app.mission3.allow-draft-content:true}")
    private boolean allowDraftContent;

    public record Result(String answersJson, int score, String summaryLine) {}

    /**
     * Kiểm tra V01–V06, tính điểm theo mục 9 tài liệu Mission 3 và trả về payload đã chuẩn hóa
     * (chỉ giữ các trường cho phép, thêm score / isValid / contentVersion do server xác nhận).
     */
    public Result grade(String selectedOptionsJson) {
        if (!allowDraftContent && ANSWER_KEY.values().stream().anyMatch(v -> !"approved".equals(v[2]))) {
            throw new IllegalStateException("Mission 3 đang chờ Ban Tổ chức phê duyệt nội dung, chưa nhận bài nộp.");
        }
        JsonNode in;
        try {
            in = selectedOptionsJson == null ? null : jsonMapper.readTree(selectedOptionsJson);
        } catch (JacksonException e) {
            in = null;
        }
        if (in == null || !in.isObject()) {
            throw new IllegalArgumentException("Dữ liệu bài nộp Mission 3 không hợp lệ.");
        }

        if (!in.path("agreed").asBoolean(false)) {
            throw new IllegalArgumentException("Vui lòng xác nhận đã hiểu yêu cầu nhiệm vụ.");
        }

        Set<String> viewed = new HashSet<>();
        in.path("viewedLeaders").forEach(n -> {
            if (ANSWER_KEY.containsKey(n.asString(""))) viewed.add(n.asString(""));
        });
        if (viewed.size() < ANSWER_KEY.size()) {
            throw new IllegalArgumentException("Hãy mở đủ năm hồ sơ lãnh đạo.");
        }

        JsonNode marks = in.path("markMatches");
        JsonNode qualities = in.path("qualityMatches");
        int correct = 0;
        boolean filled = true;
        for (Map.Entry<String, String[]> e : ANSWER_KEY.entrySet()) {
            String mark = marks.path(e.getKey()).asString("");
            String quality = qualities.path(e.getKey()).asString("");
            if (mark.isEmpty() || quality.isEmpty()) filled = false;
            if (mark.equals(e.getValue()[0])) correct++;
            if (quality.equals(e.getValue()[1])) correct++;
        }
        if (!filled) {
            throw new IllegalArgumentException("Hãy hoàn thành đủ hai lựa chọn cho mỗi người dẫn đường.");
        }
        if (correct < ANSWER_KEY.size() * 2) {
            throw new IllegalArgumentException("Một số kết nối chưa chính xác. Hãy xem lại hồ sơ và thử lại.");
        }

        JsonNode conts = in.path("continuationSelections");
        int contCount = 0;
        for (String leaderId : ANSWER_KEY.keySet()) {
            if (CONTINUATION_IDS.contains(conts.path(leaderId).asString(""))) contCount++;
        }
        if (contCount < ANSWER_KEY.size()) {
            throw new IllegalArgumentException("Hãy lựa chọn điều cần tiếp nối từ cả năm người dẫn đường.");
        }

        String reflection = in.path("leadershipReflection").asString("").trim();
        if (reflection.length() < MIN_REFLECTION_CHARS) {
            throw new IllegalArgumentException("Phần chia sẻ cần có ít nhất " + MIN_REFLECTION_CHARS + " ký tự.");
        }

        // Mở hồ sơ 10 + giải mã 70 + điều tiếp nối 10 + chia sẻ 10 = 100
        int score = viewed.size() * 2 + correct * 7 + contCount * 2 + (isMeaningful(reflection) ? 10 : 0);

        ObjectNode out = jsonMapper.createObjectNode();
        out.put("missionId", MISSION_CODE);
        out.put("contentVersion", CONTENT_VERSION);
        out.put("agreed", true);
        out.set("viewedLeaders", jsonMapper.valueToTree(List.copyOf(ANSWER_KEY.keySet())));
        ObjectNode outMarks = out.putObject("markMatches");
        ObjectNode outQualities = out.putObject("qualityMatches");
        ObjectNode outConts = out.putObject("continuationSelections");
        for (String leaderId : ANSWER_KEY.keySet()) {
            outMarks.put(leaderId, marks.path(leaderId).asString(""));
            outQualities.put(leaderId, qualities.path(leaderId).asString(""));
            outConts.put(leaderId, conts.path(leaderId).asString(""));
        }
        out.put("leadershipReflection", reflection);
        out.put("score", score);
        out.put("isValid", true);

        return new Result(jsonMapper.writeValueAsString(out), score,
                "ĐIỂM TỰ ĐỘNG (hệ thống chấm): " + score + "/100 · Bộ nội dung " + CONTENT_VERSION);
    }

    /** Không chỉ gồm ký tự lặp / vô nghĩa: đủ số từ khác nhau và không bị một ký tự chiếm phần lớn. */
    static boolean isMeaningful(String text) {
        String compact = text.replaceAll("\\s+", "");
        if (compact.isEmpty()) return false;
        Map<Integer, Integer> freq = new java.util.HashMap<>();
        compact.codePoints().forEach(c -> freq.merge(Character.toLowerCase(c), 1, Integer::sum));
        int maxFreq = freq.values().stream().max(Integer::compare).orElse(0);
        long distinctWords = java.util.Arrays.stream(text.toLowerCase().split("\\s+"))
                .filter(w -> w.length() > 1).distinct().count();
        return freq.size() >= 8 && maxFreq <= compact.length() * 0.4 && distinctWords >= 8;
    }
}
