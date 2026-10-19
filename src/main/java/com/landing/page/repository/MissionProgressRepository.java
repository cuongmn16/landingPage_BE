package com.landing.page.repository;

import com.landing.page.entity.MissionProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MissionProgressRepository extends JpaRepository<MissionProgress, Long> {
    Optional<MissionProgress> findByEmployeeIdAndMissionId(Long employeeId, Long missionId);
}
