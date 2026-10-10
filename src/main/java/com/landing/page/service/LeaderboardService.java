package com.landing.page.service;

import com.landing.page.dto.response.IndividualLeaderboardItemResponse;
import com.landing.page.dto.response.LeaderboardItemResponse;
import com.landing.page.dto.response.UnitParticipantResponse;
import com.landing.page.entity.Employee;
import com.landing.page.entity.Unit;
import com.landing.page.entity.enums.Role;
import com.landing.page.entity.enums.SubmissionStatus;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.repository.SubmissionRepository;
import com.landing.page.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final UnitRepository unitRepository;
    private final EmployeeRepository employeeRepository;
    private final SubmissionRepository submissionRepository;
    private final EmployeeService employeeService;

    @Value("${app.leaderboard.excluded-unit-codes:PB_BTC}")
    private Set<String> excludedUnitCodes;

    @Transactional(readOnly = true)
    public List<LeaderboardItemResponse> getUnitLeaderboard() {
        long minRequired = employeeService.getMinRequiredMissions();

        // Bulk-load everything once instead of querying per employee
        Map<Long, Long> validCountByEmployee = new HashMap<>();
        for (Object[] row : submissionRepository.countSubmissionsGroupedByEmployee(EmployeeService.VALID_SUBMISSION_STATUSES)) {
            validCountByEmployee.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, LocalDateTime> lastUpdatedByUnit = new HashMap<>();
        for (Object[] row : submissionRepository.findMaxLastUpdatedAtGroupedByUnit()) {
            lastUpdatedByUnit.put((Long) row[0], (LocalDateTime) row[1]);
        }
        Map<Long, List<Employee>> employeesByUnit = employeeRepository.findAll().stream()
                .collect(Collectors.groupingBy(e -> e.getUnit().getId()));

        List<LeaderboardItemResponse> leaderboard = new ArrayList<>();
        for (Unit unit : unitRepository.findAll()) {
            if (excludedUnitCodes.contains(unit.getCode())) {
                continue; // e.g. the organizing committee (BTC) does not compete
            }
            List<Employee> unitEmployees = employeesByUnit.getOrDefault(unit.getId(), List.of());

            int validParticipantsCount = (int) unitEmployees.stream()
                    .filter(e -> Boolean.TRUE.equals(e.getIsEmailVerified()))
                    .filter(e -> validCountByEmployee.getOrDefault(e.getId(), 0L) >= minRequired)
                    .count();

            int totalPersonnel = unit.getTotalPersonnel() != null && unit.getTotalPersonnel() > 0
                    ? unit.getTotalPersonnel()
                    : Math.max(1, unitEmployees.size());

            // Formula: Participation Rate % = (Valid Participants / Total Target Personnel) * 100%
            double participationRate = ((double) validParticipantsCount / totalPersonnel) * 100.0;
            double roundedRate = Math.round(participationRate * 100.0) / 100.0;

            leaderboard.add(LeaderboardItemResponse.builder()
                    .unitId(unit.getId())
                    .unitCode(unit.getCode())
                    .unitName(unit.getName())
                    .totalPersonnel(totalPersonnel)
                    .validParticipantsCount(validParticipantsCount)
                    .participationRatePercent(roundedRate)
                    .lastUpdatedAt(lastUpdatedByUnit.getOrDefault(unit.getId(), unit.getUpdatedAt()))
                    .build());
        }

        // Sort by participation rate desc, then valid participants desc
        leaderboard.sort(Comparator
                .comparing(LeaderboardItemResponse::getParticipationRatePercent).reversed()
                .thenComparing(Comparator.comparing(LeaderboardItemResponse::getValidParticipantsCount).reversed()));

        // Standard competition ranking: units with the same rate and count share a rank (1, 1, 3, ...)
        for (int i = 0; i < leaderboard.size(); i++) {
            LeaderboardItemResponse current = leaderboard.get(i);
            LeaderboardItemResponse previous = i > 0 ? leaderboard.get(i - 1) : null;
            boolean tied = previous != null
                    && previous.getParticipationRatePercent().equals(current.getParticipationRatePercent())
                    && previous.getValidParticipantsCount().equals(current.getValidParticipantsCount());
            current.setRank(tied ? previous.getRank() : i + 1);
        }
        return leaderboard;
    }

    /**
     * Players ranked by total score graded by the organizing committee (invalid submissions do not count).
     * A null limit returns every ranked player.
     */
    @Transactional(readOnly = true)
    public List<IndividualLeaderboardItemResponse> getIndividualLeaderboard(Integer limit) {
        List<Object[]> rows = submissionRepository.sumScoresGroupedByEmployee(List.of(SubmissionStatus.INVALID));
        Map<Long, Employee> employeesById = employeeRepository.findAllById(
                        rows.stream().map(row -> (Long) row[0]).toList()).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        List<IndividualLeaderboardItemResponse> leaderboard = new ArrayList<>();
        for (Object[] row : rows) {
            Employee employee = employeesById.get((Long) row[0]);
            double totalScore = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            if (employee == null || employee.getRole() == Role.ROLE_ADMIN
                    || excludedUnitCodes.contains(employee.getUnit().getCode()) || totalScore <= 0) {
                continue;
            }
            leaderboard.add(IndividualLeaderboardItemResponse.builder()
                    .employeeId(employee.getId())
                    .fullName(employee.getFullName())
                    .unitCode(employee.getUnit().getCode())
                    .unitName(employee.getUnit().getName())
                    .totalScore(Math.round(totalScore * 100.0) / 100.0)
                    .lastUpdatedAt((LocalDateTime) row[2])
                    .build());
        }

        // Highest score first; on a tie whoever reached it earlier ranks higher in the list
        leaderboard.sort(Comparator
                .comparing(IndividualLeaderboardItemResponse::getTotalScore).reversed()
                .thenComparing(IndividualLeaderboardItemResponse::getLastUpdatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        // Standard competition ranking: equal scores share a rank (1, 1, 3, ...)
        for (int i = 0; i < leaderboard.size(); i++) {
            IndividualLeaderboardItemResponse current = leaderboard.get(i);
            IndividualLeaderboardItemResponse previous = i > 0 ? leaderboard.get(i - 1) : null;
            boolean tied = previous != null && previous.getTotalScore().equals(current.getTotalScore());
            current.setRank(tied ? previous.getRank() : i + 1);
        }
        if (limit != null && limit >= 0 && leaderboard.size() > limit) {
            return new ArrayList<>(leaderboard.subList(0, limit));
        }
        return leaderboard;
    }

    /**
     * Valid participants of one unit (same rule as the unit ranking), most valid submissions first.
     */
    @Transactional(readOnly = true)
    public List<UnitParticipantResponse> getUnitParticipants(Long unitId) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Unit not found with ID: " + unitId));
        if (excludedUnitCodes.contains(unit.getCode())) {
            return List.of();
        }
        long minRequired = employeeService.getMinRequiredMissions();
        Map<Long, Long> validCountByEmployee = new HashMap<>();
        for (Object[] row : submissionRepository.countSubmissionsGroupedByEmployee(EmployeeService.VALID_SUBMISSION_STATUSES)) {
            validCountByEmployee.put((Long) row[0], (Long) row[1]);
        }

        return employeeRepository.findVerifiedEmployeesByUnitId(unitId).stream()
                .filter(e -> validCountByEmployee.getOrDefault(e.getId(), 0L) >= minRequired)
                .map(e -> UnitParticipantResponse.builder()
                        .employeeId(e.getId())
                        .fullName(e.getFullName())
                        .validSubmissions(validCountByEmployee.get(e.getId()))
                        .build())
                .sorted(Comparator.comparing(UnitParticipantResponse::getValidSubmissions).reversed()
                        .thenComparing(UnitParticipantResponse::getFullName))
                .toList();
    }
}
