package com.landing.page.service;

import com.landing.page.dto.request.MissionRequest;
import com.landing.page.entity.Mission;
import com.landing.page.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MissionService {

    private final MissionRepository missionRepository;

    @Transactional
    public Mission createOrUpdateMission(MissionRequest request) {
        Mission mission = missionRepository.findByCode(request.getCode())
                .orElseGet(() -> Mission.builder().code(request.getCode()).build());

        mission.setTitle(request.getTitle());
        mission.setDescription(request.getDescription());
        mission.setIsOpen(!Boolean.FALSE.equals(request.getIsOpen()));

        return missionRepository.save(mission);
    }

    @Transactional
    public Mission toggleMissionStatus(Long missionId, boolean isOpen) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new IllegalArgumentException("Mission not found with ID: " + missionId));
        mission.setIsOpen(isOpen);
        return missionRepository.save(mission);
    }

    @Transactional(readOnly = true)
    public List<Mission> getAllMissions() {
        return missionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Mission getMissionById(Long missionId) {
        return missionRepository.findById(missionId)
                .orElseThrow(() -> new IllegalArgumentException("Mission not found with ID: " + missionId));
    }
}
