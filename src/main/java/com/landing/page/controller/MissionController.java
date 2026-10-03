package com.landing.page.controller;

import com.landing.page.dto.request.MissionRequest;
import com.landing.page.dto.response.ApiResponse;
import com.landing.page.entity.Mission;
import com.landing.page.service.MissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;

    @PostMapping
    public ResponseEntity<ApiResponse<Mission>> createOrUpdateMission(@Valid @RequestBody MissionRequest request) {
        Mission mission = missionService.createOrUpdateMission(request);
        return ResponseEntity.ok(ApiResponse.ok("Mission saved successfully", mission));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Mission>>> getAllMissions() {
        List<Mission> missions = missionService.getAllMissions();
        return ResponseEntity.ok(ApiResponse.ok(missions));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Mission>> getMissionById(@PathVariable("id") Long id) {
        Mission mission = missionService.getMissionById(id);
        return ResponseEntity.ok(ApiResponse.ok(mission));
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<Mission>> toggleMissionStatus(
            @PathVariable("id") Long id,
            @RequestParam("isOpen") boolean isOpen) {
        Mission mission = missionService.toggleMissionStatus(id, isOpen);
        return ResponseEntity.ok(ApiResponse.ok("Mission status updated", mission));
    }
}
