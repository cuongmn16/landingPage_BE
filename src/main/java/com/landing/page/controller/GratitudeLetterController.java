package com.landing.page.controller;

import com.landing.page.dto.response.ApiResponse;
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
}
