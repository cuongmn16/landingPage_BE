package com.landing.page.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventWishResponse {

    private Long id;
    private String senderName;
    private String unitName;
    private String content;
    private Boolean isHidden;
    private LocalDateTime createdAt;
}
