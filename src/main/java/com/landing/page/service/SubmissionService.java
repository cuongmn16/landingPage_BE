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
import com.landing.page.repository.SubmissionAttachmentRepository;
import com.landing.page.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionAttachmentRepository attachmentRepository;
    private final EmployeeRepository employeeRepository;
    private final MissionRepository missionRepository;
    private final MinioService minioService;

    @Value("${app.submission.max-files-per-mission:5}")
    private int maxFilesPerMission;

    @Transactional
    public SubmissionResponse submitOrUpdate(SubmissionRequest request, List<MultipartFile> files) {
        Employee employee = null;

        // 1. Try extracting logged-in employee from Spring Security Context (JWT authentication token)
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String email = auth.getName();
            if (email != null && !email.isBlank()) {
                employee = employeeRepository.findByEmail(email.trim()).orElse(null);
            }
        }

        // 2. Fallback to request.getEmployeeId() if token resolution was not available
        if (employee == null && request.getEmployeeId() != null) {
            employee = employeeRepository.findById(request.getEmployeeId())
                    .orElse(null);
        }

        if (employee == null) {
            throw new IllegalArgumentException("Không tìm thấy thông tin nhân viên. Vui lòng đăng nhập lại tài khoản nhân viên.");
        }

        Mission mission = missionRepository.findById(request.getMissionId())
                .orElseThrow(() -> new IllegalArgumentException("Mission not found with ID: " + request.getMissionId()));

        // Check if submission already exists for this employee and mission
        Submission submission = submissionRepository
                .findByEmployeeIdAndMissionId(employee.getId(), mission.getId())
                .orElse(null);

        if (submission != null) {
            // Check edit permission: Mission must be open OR submission status REOPENED by BTC
            boolean canEdit = Boolean.TRUE.equals(mission.getIsOpen()) || submission.getStatus() == SubmissionStatus.REOPENED;
            if (!canEdit) {
                throw new IllegalStateException("Mission is closed. Players can only view status and cannot modify submission.");
            }
            log.info("Updating existing submission ID: {} for employee: {} in mission: {}", submission.getId(), employee.getEmployeeCode(), mission.getCode());
        } else {
            // New submission check: mission must be open
            if (!Boolean.TRUE.equals(mission.getIsOpen())) {
                throw new IllegalStateException("Mission is currently closed for new submissions.");
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

        // Handle uploaded files if provided
        if (files != null && !files.isEmpty()) {
            // Filter out empty files
            List<MultipartFile> validFiles = files.stream()
                    .filter(f -> f != null && !f.isEmpty())
                    .collect(Collectors.toList());

            if (!validFiles.isEmpty()) {
                if (validFiles.size() > maxFilesPerMission) {
                    throw new IllegalArgumentException("Exceeded maximum of " + maxFilesPerMission + " files allowed per Mission.");
                }

                // Delete existing attachments from MinIO and DB if updating
                if (submission.getAttachments() != null && !submission.getAttachments().isEmpty()) {
                    List<SubmissionAttachment> oldAttachments = new ArrayList<>(submission.getAttachments());
                    for (SubmissionAttachment oldAtt : oldAttachments) {
                        minioService.deleteFile(oldAtt.getObjectKey());
                        submission.removeAttachment(oldAtt);
                    }
                }

                // Upload new files
                String folder = String.format("submissions/mission_%d/emp_%d", mission.getId(), employee.getId());
                for (MultipartFile file : validFiles) {
                    minioService.validateFile(file);
                    String objectKey = minioService.uploadFile(file, folder);
                    String fileUrl = minioService.getFileUrl(objectKey);

                    SubmissionAttachment attachment = SubmissionAttachment.builder()
                            .originalFileName(file.getOriginalFilename())
                            .objectKey(objectKey)
                            .fileUrl(fileUrl)
                            .contentType(file.getContentType())
                            .fileSizeBytes(file.getSize())
                            .build();

                    submission.addAttachment(attachment);
                }
            }
        }

        Submission saved = submissionRepository.save(submission);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getAllSubmissions() {
        return submissionRepository.findAll().stream()
                .sorted((a, b) -> {
                    LocalDateTime t1 = a.getLastUpdatedAt() != null ? a.getLastUpdatedAt() : a.getSubmissionTime();
                    LocalDateTime t2 = b.getLastUpdatedAt() != null ? b.getLastUpdatedAt() : b.getSubmissionTime();
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return t2.compareTo(t1);
                })
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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
                .collect(Collectors.toList());
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

    public SubmissionResponse mapToResponse(Submission s) {
        List<AttachmentResponse> attachmentResponses = (s.getAttachments() == null) ? List.of() :
                s.getAttachments().stream()
                        .map(a -> AttachmentResponse.builder()
                                .id(a.getId())
                                .originalFileName(a.getOriginalFileName())
                                .objectKey(a.getObjectKey())
                                .fileUrl(a.getFileUrl())
                                .contentType(a.getContentType())
                                .fileSizeBytes(a.getFileSizeBytes())
                                .uploadedAt(a.getUploadedAt())
                                .build())
                        .collect(Collectors.toList());

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
