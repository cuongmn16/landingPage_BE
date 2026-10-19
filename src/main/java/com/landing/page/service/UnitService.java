package com.landing.page.service;

import com.landing.page.dto.request.UnitRequest;
import com.landing.page.dto.response.UnitResponse;
import com.landing.page.entity.Unit;
import com.landing.page.entity.enums.ApprovalStatus;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin quản lý phòng ban / đơn vị, quân số và theo dõi số người tham gia.
 */
@Service
@RequiredArgsConstructor
public class UnitService {

    /** Đơn vị của BTC (tài khoản admin) — không cho xóa / đổi mã. */
    private static final String PROTECTED_UNIT_CODE = "PB_BTC";

    private final UnitRepository unitRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<UnitResponse> getAllUnits() {
        Map<Long, Map<ApprovalStatus, Long>> counts = participantCounts();
        return unitRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(u -> mapToResponse(u, counts))
                .toList();
    }

    @Transactional
    public UnitResponse createUnit(UnitRequest request) {
        String code = normalizeCode(request.getCode());
        if (unitRepository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("Mã đơn vị đã tồn tại: " + code);
        }
        Integer sortOrder = request.getSortOrder() != null
                ? request.getSortOrder()
                : unitRepository.findMaxSortOrder() + 1;
        Unit unit = Unit.builder()
                .code(code)
                .name(request.getName().trim())
                .totalPersonnel(request.getTotalPersonnel())
                .sortOrder(sortOrder)
                .build();
        return mapToResponse(unitRepository.save(unit), participantCounts());
    }

    @Transactional
    public UnitResponse updateUnit(Long id, UnitRequest request) {
        Unit unit = findUnit(id);
        String code = normalizeCode(request.getCode());
        unitRepository.findByCode(code)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Mã đơn vị đã tồn tại: " + code);
                });
        if (PROTECTED_UNIT_CODE.equals(unit.getCode()) && !PROTECTED_UNIT_CODE.equals(code)) {
            throw new IllegalArgumentException("Không thể đổi mã của đơn vị Ban Tổ Chức.");
        }
        unit.setCode(code);
        unit.setName(request.getName().trim());
        unit.setTotalPersonnel(request.getTotalPersonnel());
        if (request.getSortOrder() != null) {
            unit.setSortOrder(request.getSortOrder());
        }
        return mapToResponse(unitRepository.save(unit), participantCounts());
    }

    @Transactional
    public void deleteUnit(Long id) {
        Unit unit = findUnit(id);
        if (PROTECTED_UNIT_CODE.equals(unit.getCode())) {
            throw new IllegalStateException("Không thể xóa đơn vị Ban Tổ Chức.");
        }
        long registered = employeeRepository.countByUnitId(id);
        if (registered > 0) {
            throw new IllegalStateException("Đơn vị đang có " + registered
                    + " nhân sự đã đăng ký. Hãy chuyển hoặc xóa nhân sự trước khi xóa đơn vị.");
        }
        unitRepository.delete(unit);
    }

    private Unit findUnit(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn vị với ID: " + id));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    /** unitId → (trạng thái duyệt → số CBNV), không tính admin. */
    private Map<Long, Map<ApprovalStatus, Long>> participantCounts() {
        Map<Long, Map<ApprovalStatus, Long>> result = new HashMap<>();
        for (Object[] row : employeeRepository.countParticipantsGroupedByUnitAndStatus()) {
            result.computeIfAbsent((Long) row[0], k -> new HashMap<>())
                    .put((ApprovalStatus) row[1], (Long) row[2]);
        }
        return result;
    }

    private UnitResponse mapToResponse(Unit u, Map<Long, Map<ApprovalStatus, Long>> counts) {
        Map<ApprovalStatus, Long> byStatus = counts.getOrDefault(u.getId(), Map.of());
        long participants = byStatus.getOrDefault(ApprovalStatus.ACCEPTED, 0L);
        long pending = byStatus.getOrDefault(ApprovalStatus.PENDING, 0L);
        int total = u.getTotalPersonnel() != null ? u.getTotalPersonnel() : 0;
        double rate = total > 0 ? Math.round(participants * 1000.0 / total) / 10.0 : 0.0;

        return UnitResponse.builder()
                .id(u.getId())
                .code(u.getCode())
                .name(u.getName())
                .totalPersonnel(u.getTotalPersonnel())
                .sortOrder(u.getSortOrder())
                .participantCount(participants)
                .pendingCount(pending)
                .registeredCount(employeeRepository.countByUnitId(u.getId()))
                .participationRate(rate)
                .updatedAt(u.getUpdatedAt())
                .build();
    }
}
