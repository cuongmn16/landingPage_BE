package com.landing.page.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionProgressRequest {

    /** Trạng thái chơi do frontend tự định nghĩa (chuỗi JSON). */
    @NotBlank(message = "Tiến trình không được để trống")
    @Size(max = 200000, message = "Dữ liệu tiến trình quá lớn")
    private String progressJson;
}
