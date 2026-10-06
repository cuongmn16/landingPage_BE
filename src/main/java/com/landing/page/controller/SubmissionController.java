package com.landing.page.controller;

import com.landing.page.dto.request.SubmissionRequest;
import com.landing.page.dto.response.ApiResponse;
import com.landing.page.dto.response.SubmissionResponse;
import com.landing.page.entity.enums.SubmissionStatus;
import com.landing.page.service.MinioService;
import com.landing.page.service.SubmissionService;
import io.minio.StatObjectResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final MinioService minioService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SubmissionResponse>> submitOrUpdate(
            @Valid @RequestPart("submission") SubmissionRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        SubmissionResponse response = submissionService.submitOrUpdate(request, files);
        return ResponseEntity.ok(ApiResponse.ok("Submission saved successfully", response));
    }

    @GetMapping("/files/download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @RequestParam("key") String objectKey,
            @RequestParam(value = "token", required = false) String token,
            @RequestParam(value = "download", required = false, defaultValue = "false") boolean download) {
        submissionService.assertCanDownload(objectKey, token);

        StatObjectResponse stat = minioService.getFileStat(objectKey);
        if (stat == null) {
            throw new IllegalArgumentException("Không tìm thấy file.");
        }
        InputStream stream = minioService.getFileStream(objectKey);

        String contentType = (stat.contentType() != null && !stat.contentType().isBlank())
                ? stat.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        // Object key format: <folder>/<uuid>_<originalFileName>
        String baseName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        String fileName = baseName.contains("_") ? baseName.substring(baseName.indexOf('_') + 1) : baseName;

        HttpHeaders headers = new HttpHeaders();
        try {
            headers.setContentType(MediaType.parseMediaType(contentType));
        } catch (Exception e) {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }

        ContentDisposition disposition = download
                ? ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build()
                : ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build();
        headers.setContentDisposition(disposition);
        headers.set("X-Content-Type-Options", "nosniff");

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(stat.size())
                .body(new InputStreamResource(stream));
    }

    /**
     * Submissions of the logged-in participant.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<SubmissionResponse>>> getMySubmissions() {
        return ResponseEntity.ok(ApiResponse.ok(submissionService.getMySubmissions()));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<SubmissionResponse>>> getAllSubmissions() {
        return ResponseEntity.ok(ApiResponse.ok(submissionService.getAllSubmissions()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> getSubmission(
            @RequestParam(value = "employeeId", required = false) Long employeeId,
            @RequestParam(value = "missionId", required = false) Long missionId) {
        if (employeeId != null && missionId != null) {
            return ResponseEntity.ok(ApiResponse.ok(submissionService.getSubmission(employeeId, missionId)));
        }
        return ResponseEntity.ok(ApiResponse.ok(submissionService.getAllSubmissions()));
    }

    @GetMapping("/mission/{missionId}")
    public ResponseEntity<ApiResponse<List<SubmissionResponse>>> getSubmissionsByMission(
            @PathVariable("missionId") Long missionId) {
        return ResponseEntity.ok(ApiResponse.ok(submissionService.getSubmissionsByMission(missionId)));
    }

    @PutMapping("/{id}/reopen")
    public ResponseEntity<ApiResponse<SubmissionResponse>> reopenSubmission(@PathVariable("id") Long id) {
        SubmissionResponse response = submissionService.reopenSubmission(id);
        return ResponseEntity.ok(ApiResponse.ok("Submission reopened for editing by BTC", response));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SubmissionResponse>> updateStatus(
            @PathVariable("id") Long id,
            @RequestParam(value = "status", required = false) String statusStr,
            @RequestParam(value = "score", required = false) Double score) {
        SubmissionStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = SubmissionStatus.valueOf(statusStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Trạng thái bài nộp không hợp lệ: " + statusStr);
            }
        }
        SubmissionResponse response = submissionService.updateSubmissionStatus(id, status, score);
        return ResponseEntity.ok(ApiResponse.ok("Submission status and score updated", response));
    }
}
