package com.landing.page.service;

import com.landing.page.dto.request.MissionProgressRequest;
import com.landing.page.dto.response.MissionProgressResponse;
import com.landing.page.entity.Employee;
import com.landing.page.entity.Mission;
import com.landing.page.entity.MissionProgress;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.repository.MissionProgressRepository;
import com.landing.page.repository.MissionRepository;
import com.landing.page.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MissionProgressService {

    private final MissionProgressRepository progressRepository;
    private final EmployeeRepository employeeRepository;
    private final MissionRepository missionRepository;

    /** Tiến trình của user hiện tại; null nếu chưa có. */
    @Transactional(readOnly = true)
    public MissionProgressResponse getMyProgress(Long missionId) {
        Employee employee = currentEmployee();
        return progressRepository.findByEmployeeIdAndMissionId(employee.getId(), missionId)
                .map(this::mapToResponse)
                .orElse(null);
    }

    @Transactional
    public MissionProgressResponse saveMyProgress(Long missionId, MissionProgressRequest request) {
        Employee employee = currentEmployee();
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Mission với ID: " + missionId));

        MissionProgress progress = progressRepository.findByEmployeeIdAndMissionId(employee.getId(), missionId)
                .orElseGet(() -> MissionProgress.builder().employee(employee).mission(mission).build());
        progress.setProgressJson(request.getProgressJson());
        return mapToResponse(progressRepository.save(progress));
    }

    @Transactional
    public void deleteMyProgress(Long missionId) {
        Employee employee = currentEmployee();
        progressRepository.findByEmployeeIdAndMissionId(employee.getId(), missionId)
                .ifPresent(progressRepository::delete);
    }

    private Employee currentEmployee() {
        String email = SecurityUtils.currentEmail()
                .orElseThrow(() -> new IllegalArgumentException("Vui lòng đăng nhập lại tài khoản nhân viên."));
        return employeeRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin nhân viên. Vui lòng đăng nhập lại."));
    }

    private MissionProgressResponse mapToResponse(MissionProgress p) {
        return MissionProgressResponse.builder()
                .missionId(p.getMission().getId())
                .progressJson(p.getProgressJson())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
