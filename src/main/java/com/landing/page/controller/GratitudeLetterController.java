package com.landing.page.controller;

import com.landing.page.dto.response.ApiResponse;
import com.landing.page.dto.response.GratitudeLetterAdminSummaryResponse;
import com.landing.page.dto.response.GratitudeLetterResponse;
import com.landing.page.service.GratitudeLetterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gratitude-letters")
@RequiredArgsConstructor
public class GratitudeLetterController {

    private final GratitudeLetterService gratitudeLetterService;

    @GetMapping("/my-letter")
    public ResponseEntity<ApiResponse<GratitudeLetterResponse>> getMyLetter() {
        return ResponseEntity.ok(ApiResponse.ok(gratitudeLetterService.getMyLetter()));
    }

    @PostMapping("/open")
    public ResponseEntity<ApiResponse<Void>> markOpened() {
        gratitudeLetterService.markLetterOpened();
        return ResponseEntity.ok(ApiResponse.ok("Đã ghi nhận mở thư tri ân", null));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Boolean>> getStatus() {
        return ResponseEntity.ok(ApiResponse.ok("Trạng thái phát hành thư tri ân", gratitudeLetterService.isPublished()));
    }

    @GetMapping("/admin/list")
    public ResponseEntity<ApiResponse<GratitudeLetterAdminSummaryResponse>> getAdminList() {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách thư tri ân quản trị", gratitudeLetterService.getAdminSummary()));
    }

    @GetMapping("/admin/preview/{id}")
    public ResponseEntity<ApiResponse<GratitudeLetterResponse>> previewLetter(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Xem trước thư tri ân nhân sự", gratitudeLetterService.getLetterForAdminPreview(id)));
    }

    @PostMapping("/admin/publish")
    public ResponseEntity<ApiResponse<Boolean>> setPublished(@RequestParam("published") boolean published) {
        boolean result = gratitudeLetterService.setPublished(published);
        return ResponseEntity.ok(ApiResponse.ok(published ? "Đã phát hành Thư Tri Ân tới toàn bộ nhân sự!" : "Đã tạm khóa phát hành Thư Tri Ân.", result));
    }

    @PostMapping("/admin/publish-mode")
    public ResponseEntity<ApiResponse<String>> setPublishMode(@RequestParam("mode") String mode) {
        String result = gratitudeLetterService.setPublishMode(mode);
        String msg = "AUTO_20H".equalsIgnoreCase(result)
                ? "Đã kích hoạt chế độ tự động phát hành lúc 20:00!"
                : ("MANUAL_PUBLISHED".equalsIgnoreCase(result)
                    ? "Đã phát hành Thư Tri Ân chủ động ngay lập tức!"
                    : "Đã tạm khóa niêm phong Thư Tri Ân.");
        return ResponseEntity.ok(ApiResponse.ok(msg, result));
    }
}
