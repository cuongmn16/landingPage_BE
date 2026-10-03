package com.landing.page.dto.response;

import com.landing.page.entity.enums.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponse {
    private Long id;
    
    // Employee Info
    private Long employeeId;
    private String employeeCode;
    private String email;
    private String fullName;
    private String unitCode;
    private String unitName;

    // Mission Info
    private Long missionId;
    private String missionCode;
    private String missionTitle;

    // Answers
    private String longTextAnswer;
    private String selectedOptionsJson;
    private String orderedDataJson;
    private String evidenceSource;
    private String witnessName;

    // Attachments
    private List<AttachmentResponse> attachments;

    // Submission Metadata
    private SubmissionStatus status;
    private Double score;
    private LocalDateTime submissionTime;
    private LocalDateTime lastUpdatedAt;
}
