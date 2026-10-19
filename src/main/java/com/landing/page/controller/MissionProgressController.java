package com.landing.page.controller;

import com.landing.page.dto.request.MissionProgressRequest;
import com.landing.page.dto.response.ApiResponse;
import com.landing.page.dto.response.MissionProgressResponse;
import com.landing.page.service.MissionProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Bản nháp tiến trình chơi Mission của người dùng đang đăng nhập.
 */
@RestController
@RequestMapping("/api/missions/{missionId}/progress")
@RequiredArgsConstructor
public class MissionProgressController {

    private final MissionProgressService progressService;

    @GetMapping
    public ResponseEntity<ApiResponse<MissionProgressResponse>> getMyProgress(@PathVariable("missionId") Long missionId) {
        return ResponseEntity.ok(ApiResponse.ok(progressService.getMyProgress(missionId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<MissionProgressResponse>> saveMyProgress(
            @PathVariable("missionId") Long missionId,
            @Valid @RequestBody MissionProgressRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã lưu tiến trình", progressService.saveMyProgress(missionId, request)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteMyProgress(@PathVariable("missionId") Long missionId) {
        progressService.deleteMyProgress(missionId);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa tiến trình", null));
    }
}
