package com.landing.page.service;

import com.landing.page.dto.response.LeaderboardItemResponse;
import com.landing.page.entity.Employee;
import com.landing.page.entity.Unit;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.repository.SubmissionRepository;
import com.landing.page.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final UnitRepository unitRepository;
    private final EmployeeRepository employeeRepository;
    private final SubmissionRepository submissionRepository;
    private final EmployeeService employeeService;

    @Transactional(readOnly = true)
    public List<LeaderboardItemResponse> getUnitLeaderboard() {
        List<Unit> units = unitRepository.findAll();
        List<LeaderboardItemResponse> leaderboard = new ArrayList<>();

        for (Unit unit : units) {
            List<Employee> unitEmployees = employeeRepository.findByUnitId(unit.getId());
            
            // Calculate number of valid participants in unit
            int validParticipantsCount = 0;
            for (Employee emp : unitEmployees) {
                if (employeeService.checkValidParticipant(emp.getId()).isValidParticipant()) {
                    validParticipantsCount++;
                }
            }

            int totalPersonnel = unit.getTotalPersonnel() != null && unit.getTotalPersonnel() > 0 
                    ? unit.getTotalPersonnel() 
                    : Math.max(1, unitEmployees.size());

            // Formula: Participation Rate % = (Valid Participants / Total Target Personnel) * 100%
            double participationRate = ((double) validParticipantsCount / totalPersonnel) * 100.0;
            // Round to 2 decimal places
            double roundedRate = Math.round(participationRate * 100.0) / 100.0;

            // Get last updated timestamp of submissions for this unit
            LocalDateTime lastUpdated = submissionRepository.findMaxLastUpdatedAtByUnitId(unit.getId())
                    .orElse(unit.getUpdatedAt());

            leaderboard.add(LeaderboardItemResponse.builder()
                    .unitId(unit.getId())
                    .unitCode(unit.getCode())
                    .unitName(unit.getName())
                    .totalPersonnel(totalPersonnel)
                    .validParticipantsCount(validParticipantsCount)
                    .participationRatePercent(roundedRate)
                    .lastUpdatedAt(lastUpdated)
                    .build());
        }

        // Sort leaderboard by participationRatePercent descending, then validParticipantsCount descending
        leaderboard.sort(Comparator
                .comparing(LeaderboardItemResponse::getParticipationRatePercent).reversed()
                .thenComparing(Comparator.comparing(LeaderboardItemResponse::getValidParticipantsCount).reversed()));

        // Assign ranks (1, 2, 3...)
        for (int i = 0; i < leaderboard.size(); i++) {
            leaderboard.get(i).setRank(i + 1);
        }

        return leaderboard;
    }
}
