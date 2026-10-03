package com.landing.page.controller;

import com.landing.page.dto.request.EmployeeImportRequest;
import com.landing.page.dto.request.UnitRequest;
import com.landing.page.dto.response.ApiResponse;
import com.landing.page.dto.response.ValidParticipantResponse;
import com.landing.page.entity.Employee;
import com.landing.page.entity.Unit;
import com.landing.page.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<Employee>>> getAllEmployees() {
        return ResponseEntity.ok(ApiResponse.ok(employeeService.getAllEmployees()));
    }

    @GetMapping("/units")
    public ResponseEntity<ApiResponse<java.util.List<Unit>>> getAllUnits() {
        return ResponseEntity.ok(ApiResponse.ok(employeeService.getAllUnits()));
    }

    @PostMapping("/units")
    public ResponseEntity<ApiResponse<Unit>> createOrUpdateUnit(@Valid @RequestBody UnitRequest request) {
        Unit unit = employeeService.createOrUpdateUnit(request);
        return ResponseEntity.ok(ApiResponse.ok("Unit saved successfully", unit));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Employee>> registerEmployee(@Valid @RequestBody EmployeeImportRequest request) {
        Employee employee = employeeService.importEmployee(request);
        return ResponseEntity.ok(ApiResponse.ok("Employee registered successfully", employee));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<Employee>> importEmployee(@Valid @RequestBody EmployeeImportRequest request) {
        Employee employee = employeeService.importEmployee(request);
        return ResponseEntity.ok(ApiResponse.ok("Employee imported/updated successfully", employee));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Employee>> approveEmployee(@PathVariable("id") Long id) {
        Employee employee = employeeService.approveEmployee(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã phê duyệt tài khoản và gửi email mật khẩu thành công!", employee));
    }

    @DeleteMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Employee>> rejectEmployee(@PathVariable("id") Long id) {
        Employee employee = employeeService.rejectEmployee(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã từ chối (REJECTED) yêu cầu đăng ký của nhân sự.", employee));
    }

    @GetMapping("/{id}/valid-status")
    public ResponseEntity<ApiResponse<ValidParticipantResponse>> checkValidStatus(@PathVariable("id") Long id) {
        ValidParticipantResponse response = employeeService.checkValidParticipant(id);
        return ResponseEntity.ok(ApiResponse.ok("Valid participant status retrieved", response));
    }
}
