package com.landing.page.controller;

import com.landing.page.dto.request.EventWishRequest;
import com.landing.page.dto.response.ApiResponse;
import com.landing.page.dto.response.EventWishResponse;
import com.landing.page.dto.response.LiveWishesWallResponse;
import com.landing.page.service.EventWishService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wishes")
@RequiredArgsConstructor
public class EventWishController {

    private final EventWishService eventWishService;

    /**
     * Public: Lấy danh sách lời chúc đang hiển thị cho màn hình LED sự kiện (/wishes)
     */
    @GetMapping("/live")
    public ResponseEntity<ApiResponse<LiveWishesWallResponse>> getLiveWishes() {
        return ResponseEntity.ok(ApiResponse.ok(eventWishService.getLiveWishes()));
    }

    /**
     * Public: Nhân viên quét QR trên điện thoại và gửi lời chúc mới (/gui-loi-chuc)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<EventWishResponse>> createWish(@Valid @RequestBody EventWishRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi lời chúc lên màn hình sự kiện!", eventWishService.createWish(request)));
    }

    /**
     * Admin (BTC): Xem toàn bộ lời chúc (kể cả đã ẩn)
     */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<EventWishResponse>>> getAllWishes() {
        return ResponseEntity.ok(ApiResponse.ok(eventWishService.getAllWishesForAdmin()));
    }

    /**
     * Admin (BTC): Ẩn / Hiện khẩn cấp lời chúc trên màn hình lớn
     */
    @PutMapping("/{id}/hide")
    public ResponseEntity<ApiResponse<EventWishResponse>> toggleHideWish(
            @PathVariable("id") Long id,
            @RequestParam(name = "hidden", defaultValue = "true") boolean hidden) {
        String msg = hidden ? "Đã ẩn lời chúc khỏi màn hình sự kiện" : "Đã hiển thị lại lời chúc";
        return ResponseEntity.ok(ApiResponse.ok(msg, eventWishService.toggleHideWish(id, hidden)));
    }

    /**
     * Admin (BTC): Xóa lời chúc
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWish(@PathVariable("id") Long id) {
        eventWishService.deleteWish(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa lời chúc", null));
    }
}
