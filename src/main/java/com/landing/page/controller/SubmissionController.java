package com.landing.page.controller;

import com.landing.page.dto.request.SubmissionRequest;
import com.landing.page.dto.response.ApiResponse;
import com.landing.page.dto.response.SubmissionResponse;
import com.landing.page.entity.enums.SubmissionStatus;
import com.landing.page.service.MinioService;
import com.landing.page.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import io.minio.StatObjectResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;

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
            @RequestParam(value = "download", required = false, defaultValue = "false") boolean download) {
        InputStream stream = minioService.getFileStream(objectKey);
        StatObjectResponse stat = minioService.getFileStat(objectKey);

        String contentType = (stat != null && stat.contentType() != null && !stat.contentType().isBlank())
                ? stat.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        String fileName = "file";
        if (objectKey.contains("_")) {
            fileName = objectKey.substring(objectKey.indexOf("_") + 1);
        } else if (objectKey.contains("/")) {
            fileName = objectKey.substring(objectKey.lastIndexOf("/") + 1);
        }

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

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(stat != null ? stat.size() : -1)
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<SubmissionResponse>>> getAllSubmissions() {
        List<SubmissionResponse> list = submissionService.getAllSubmissions();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> getSubmission(
            @RequestParam(value = "employeeId", required = false) Long employeeId,
            @RequestParam(value = "missionId", required = false) Long missionId) {
        if (employeeId != null && missionId != null) {
            SubmissionResponse response = submissionService.getSubmission(employeeId, missionId);
            return ResponseEntity.ok(ApiResponse.ok(response));
        }
        List<SubmissionResponse> list = submissionService.getAllSubmissions();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/mission/{missionId}")
    public ResponseEntity<ApiResponse<List<SubmissionResponse>>> getSubmissionsByMission(
            @PathVariable("missionId") Long missionId) {
        List<SubmissionResponse> list = submissionService.getSubmissionsByMission(missionId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PutMapping("/{id}/reopen")
    public ResponseEntity<ApiResponse<SubmissionResponse>> reopenSubmission(@PathVariable("id") Long id) {
        SubmissionResponse response = submissionService.reopenSubmission(id);
        return ResponseEntity.ok(ApiResponse.ok("Submission reopened for editing by BTC", response));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SubmissionResponse>> updateStatus(
            @PathVariable("id") Long id,
            @RequestParam(value = "status", required = false) SubmissionStatus status,
            @RequestParam(value = "score", required = false) Double score) {
        SubmissionResponse response = submissionService.updateSubmissionStatus(id, status, score);
        return ResponseEntity.ok(ApiResponse.ok("Submission status and score updated", response));
    }
}
