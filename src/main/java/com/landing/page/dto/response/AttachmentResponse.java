package com.landing.page.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentResponse {
    private Long id;
    private String originalFileName;
    private String objectKey;
    private String fileUrl;
    private String contentType;
    private Long fileSizeBytes;
    private LocalDateTime uploadedAt;
}
