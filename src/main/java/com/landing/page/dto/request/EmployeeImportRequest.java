package com.landing.page.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeImportRequest {

    private String employeeCode;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Địa chỉ Email không hợp lệ")
    private String email;

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    @NotBlank(message = "Vui lòng chọn phòng ban")
    private String unitCode;

    private String password;

    // Only used by admin import. Wrapper type: Jackson 3 rejects a missing value for primitives. null = true
    private Boolean emailVerified;
}
