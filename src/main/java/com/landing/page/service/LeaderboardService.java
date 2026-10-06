package com.landing.page.service;

import com.landing.page.dto.response.LeaderboardItemResponse;
import com.landing.page.entity.Employee;
import com.landing.page.entity.Unit;
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
}
