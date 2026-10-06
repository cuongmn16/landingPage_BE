package com.landing.page.service;

import com.landing.page.dto.request.SubmissionRequest;
import com.landing.page.dto.response.AttachmentResponse;
import com.landing.page.dto.response.SubmissionResponse;
import com.landing.page.entity.Employee;
import com.landing.page.entity.Mission;
import com.landing.page.entity.Submission;
import com.landing.page.entity.SubmissionAttachment;
import com.landing.page.entity.enums.SubmissionStatus;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.repository.MissionRepository;
import com.landing.page.repository.SubmissionRepository;
import com.landing.page.security.JwtTokenProvider;
import com.landing.page.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final EmployeeRepository employeeRepository;
    private final MissionRepository missionRepository;
    private final MinioService minioService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.submission.max-files-per-mission:5}")
    private int maxFilesPerMission;

    @Transactional
    public SubmissionResponse submitOrUpdate(SubmissionRequest request, List<MultipartFile> files) {
        // The submitter is always the authenticated user; request.employeeId is ignored on purpose.
        Employee employee = currentEmployee();

        Mission mission = missionRepository.findById(request.getMissionId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Mission với ID: " + request.getMissionId()));

        boolean missionOpen = Boolean.TRUE.equals(mission.getIsOpen())
                && (mission.getEndTime() == null || LocalDateTime.now().isBefore(mission.getEndTime()));

        Submission submission = submissionRepository
                .findByEmployeeIdAndMissionId(employee.getId(), mission.getId())
                .orElse(null);

        if (submission != null) {
            // Edit allowed while mission is open, or when BTC re-opened this submission
            if (!missionOpen && submission.getStatus() != SubmissionStatus.REOPENED) {
                throw new IllegalStateException("Mission đã đóng. Bạn chỉ có thể xem trạng thái, không thể sửa bài nộp.");
            }
            log.info("Updating submission ID: {} for employee: {} in mission: {}", submission.getId(), employee.getEmployeeCode(), mission.getCode());
        } else {
            if (!missionOpen) {
                throw new IllegalStateException("Mission hiện đang đóng, không nhận bài nộp mới.");
            }
            submission = Submission.builder()
                    .employee(employee)
                    .mission(mission)
                    .submissionTime(LocalDateTime.now())
                    .status(SubmissionStatus.SUBMITTED)
                    .build();
            log.info("Creating new submission for employee: {} in mission: {}", employee.getEmployeeCode(), mission.getCode());
        }

        // Update latest content fields (MVP rule: save latest version)
        submission.setLongTextAnswer(request.getLongTextAnswer());
        submission.setSelectedOptionsJson(request.getSelectedOptionsJson());
        submission.setOrderedDataJson(request.getOrderedDataJson());
        submission.setEvidenceSource(request.getEvidenceSource());
        submission.setWitnessName(request.getWitnessName());
        submission.setLastUpdatedAt(LocalDateTime.now());
        if (submission.getStatus() == SubmissionStatus.REOPENED) {
            submission.setStatus(SubmissionStatus.SUBMITTED); // Reset to SUBMITTED after edit
        }

        List<MultipartFile> validFiles = files == null ? List.of() : files.stream()
                .filter(f -> f != null && !f.isEmpty())
                .toList();

        if (!validFiles.isEmpty()) {
            if (validFiles.size() > maxFilesPerMission) {
                throw new IllegalArgumentException("Tối đa " + maxFilesPerMission + " tệp cho mỗi Mission.");
            }
            validFiles.forEach(minioService::validateFile);

            // Old objects are removed from MinIO only after the DB commit succeeds;
            // newly uploaded objects are removed if the transaction rolls back.
            List<String> oldKeys = new ArrayList<>();
            for (SubmissionAttachment oldAtt : new ArrayList<>(submission.getAttachments())) {
                oldKeys.add(oldAtt.getObjectKey());
                submission.removeAttachment(oldAtt);
            }
            List<String> newKeys = new ArrayList<>();
            registerStorageCleanup(oldKeys, newKeys);

            String folder = String.format("submissions/mission_%d/emp_%d", mission.getId(), employee.getId());
            for (MultipartFile file : validFiles) {
                String objectKey = minioService.uploadFile(file, folder);
                newKeys.add(objectKey);

                submission.addAttachment(SubmissionAttachment.builder()
                        .originalFileName(file.getOriginalFilename())
                        .objectKey(objectKey)
                        .fileUrl(minioService.getFileUrl(objectKey))
                        .contentType(file.getContentType())
                        .fileSizeBytes(file.getSize())
                        .build());
            }
        }

        return mapToResponse(submissionRepository.save(submission));
    }

    private void registerStorageCleanup(List<String> deleteOnCommit, List<String> deleteOnRollback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                List<String> keys = status == STATUS_COMMITTED ? deleteOnCommit : deleteOnRollback;
                keys.forEach(minioService::deleteFile);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getMySubmissions() {
        return submissionRepository.findByEmployeeId(currentEmployee().getId()).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getAllSubmissions() {
        return submissionRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        (Submission s) -> s.getLastUpdatedAt() != null ? s.getLastUpdatedAt() : s.getSubmissionTime(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getSubmission(Long employeeId, Long missionId) {
        Submission submission = submissionRepository.findByEmployeeIdAndMissionId(employeeId, missionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found for employee " + employeeId + " and mission " + missionId));
        return mapToResponse(submission);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getSubmissionsByMission(Long missionId) {
        return submissionRepository.findByMissionId(missionId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public SubmissionResponse reopenSubmission(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found with ID: " + submissionId));
        submission.setStatus(SubmissionStatus.REOPENED);
        return mapToResponse(submissionRepository.save(submission));
    }

    @Transactional
    public SubmissionResponse updateSubmissionStatus(Long submissionId, SubmissionStatus status, Double score) {
        if (score != null && (score < 0 || score > 100)) {
            throw new IllegalArgumentException("Điểm không hợp lệ (0–100).");
        }
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found with ID: " + submissionId));
        if (status != null) {
            submission.setStatus(status);
        }
        if (score != null) {
            submission.setScore(score);
        }
        return mapToResponse(submissionRepository.save(submission));
    }

    /**
     * Check the signed download token issued in {@link #mapToResponse}.
     */
    public void assertCanDownload(String objectKey, String token) {
        if (objectKey == null || token == null || !jwtTokenProvider.validateFileToken(token, objectKey)) {
            throw new org.springframework.security.access.AccessDeniedException("Link tải file không hợp lệ hoặc đã hết hạn. Vui lòng tải lại trang.");
        }
    }

    private Employee currentEmployee() {
        String email = SecurityUtils.currentEmail()
                .orElseThrow(() -> new IllegalArgumentException("Vui lòng đăng nhập lại tài khoản nhân viên."));
        return employeeRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin nhân viên. Vui lòng đăng nhập lại."));
    }

    private String signedFileUrl(String objectKey) {
        return minioService.getFileUrl(objectKey)
                + "&token=" + URLEncoder.encode(jwtTokenProvider.generateFileToken(objectKey), StandardCharsets.UTF_8);
    }

    public SubmissionResponse mapToResponse(Submission s) {
        List<AttachmentResponse> attachmentResponses = s.getAttachments() == null ? List.of() :
                s.getAttachments().stream()
                        .filter(Objects::nonNull)
                        .map(a -> AttachmentResponse.builder()
                                .id(a.getId())
                                .originalFileName(a.getOriginalFileName())
                                .objectKey(a.getObjectKey())
                                .fileUrl(signedFileUrl(a.getObjectKey()))
                                .contentType(a.getContentType())
                                .fileSizeBytes(a.getFileSizeBytes())
                                .uploadedAt(a.getUploadedAt())
                                .build())
                        .toList();

        return SubmissionResponse.builder()
                .id(s.getId())
                .employeeId(s.getEmployee().getId())
                .employeeCode(s.getEmployee().getEmployeeCode())
                .email(s.getEmployee().getEmail())
                .fullName(s.getEmployee().getFullName())
                .unitCode(s.getEmployee().getUnit().getCode())
                .unitName(s.getEmployee().getUnit().getName())
                .missionId(s.getMission().getId())
                .missionCode(s.getMission().getCode())
                .missionTitle(s.getMission().getTitle())
                .longTextAnswer(s.getLongTextAnswer())
                .selectedOptionsJson(s.getSelectedOptionsJson())
                .orderedDataJson(s.getOrderedDataJson())
                .evidenceSource(s.getEvidenceSource())
                .witnessName(s.getWitnessName())
                .attachments(attachmentResponses)
                .status(s.getStatus())
                .score(s.getScore())
                .submissionTime(s.getSubmissionTime())
                .lastUpdatedAt(s.getLastUpdatedAt())
                .build();
    }
}
