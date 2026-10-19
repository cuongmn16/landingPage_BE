package com.landing.page.controller;

import com.landing.page.dto.request.UnitRequest;
import com.landing.page.dto.response.ApiResponse;
import com.landing.page.dto.response.UnitResponse;
import com.landing.page.service.UnitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin (BTC): thêm / sửa / xóa đơn vị và quân số của đơn vị.
 */
@RestController
@RequestMapping("/api/units")
@RequiredArgsConstructor
public class UnitController {

    private final UnitService unitService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UnitResponse>>> getAllUnits() {
        return ResponseEntity.ok(ApiResponse.ok(unitService.getAllUnits()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UnitResponse>> createUnit(@Valid @RequestBody UnitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã thêm đơn vị", unitService.createUnit(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UnitResponse>> updateUnit(@PathVariable("id") Long id,
                                                                @Valid @RequestBody UnitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật đơn vị", unitService.updateUnit(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUnit(@PathVariable("id") Long id) {
        unitService.deleteUnit(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa đơn vị", null));
    }
}
