package com.landing.page.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventWishRequest {

    @NotBlank(message = "Vui lòng nhập họ và tên người gửi")
    @Size(max = 100, message = "Họ tên không vượt quá 100 ký tự")
    private String senderName;

    @NotBlank(message = "Vui lòng chọn đơn vị / phòng ban")
    @Size(max = 150, message = "Tên đơn vị không vượt quá 150 ký tự")
    private String unitName;

    @NotBlank(message = "Vui lòng nhập nội dung lời chúc")
    @Size(max = 300, message = "Lời chúc không vượt quá 300 ký tự")
    private String content;
}
