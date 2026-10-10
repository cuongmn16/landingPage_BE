package com.landing.page.controller;

import com.landing.page.dto.response.ApiResponse;
import com.landing.page.dto.response.IndividualLeaderboardItemResponse;
import com.landing.page.dto.response.LeaderboardItemResponse;
import com.landing.page.dto.response.UnitParticipantResponse;
import com.landing.page.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping("/units")
    public ResponseEntity<ApiResponse<List<LeaderboardItemResponse>>> getUnitLeaderboard() {
        List<LeaderboardItemResponse> leaderboard = leaderboardService.getUnitLeaderboard();
        return ResponseEntity.ok(ApiResponse.ok("Unit Leaderboard fetched successfully", leaderboard));
    }

    @GetMapping("/individuals")
    public ResponseEntity<ApiResponse<List<IndividualLeaderboardItemResponse>>> getIndividualLeaderboard(
            @RequestParam(value = "limit", required = false) Integer limit) {
        List<IndividualLeaderboardItemResponse> leaderboard = leaderboardService.getIndividualLeaderboard(limit);
        return ResponseEntity.ok(ApiResponse.ok("Individual Leaderboard fetched successfully", leaderboard));
    }

    @GetMapping("/units/{unitId}/participants")
    public ResponseEntity<ApiResponse<List<UnitParticipantResponse>>> getUnitParticipants(@PathVariable("unitId") Long unitId) {
        List<UnitParticipantResponse> participants = leaderboardService.getUnitParticipants(unitId);
        return ResponseEntity.ok(ApiResponse.ok("Unit participants fetched successfully", participants));
    }
}
