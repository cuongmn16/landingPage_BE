package com.landing.page.repository;

import com.landing.page.entity.Submission;
import com.landing.page.entity.enums.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByEmployeeIdAndMissionId(Long employeeId, Long missionId);

    List<Submission> findByEmployeeId(Long employeeId);

    List<Submission> findByMissionId(Long missionId);

    @Query("SELECT s FROM Submission s WHERE s.employee.unit.id = :unitId")
    List<Submission> findByUnitId(@Param("unitId") Long unitId);

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.employee.id = :employeeId AND s.status IN :statuses")
    long countValidSubmissionsByEmployee(@Param("employeeId") Long employeeId, @Param("statuses") List<SubmissionStatus> statuses);

    @Query("SELECT MAX(s.lastUpdatedAt) FROM Submission s WHERE s.employee.unit.id = :unitId")
    Optional<LocalDateTime> findMaxLastUpdatedAtByUnitId(@Param("unitId") Long unitId);

    /** Rows of [employeeId, count] for submissions in the given statuses. */
    @Query("SELECT s.employee.id, COUNT(s) FROM Submission s WHERE s.status IN :statuses GROUP BY s.employee.id")
    List<Object[]> countSubmissionsGroupedByEmployee(@Param("statuses") List<SubmissionStatus> statuses);

    /** Rows of [unitId, max(lastUpdatedAt)]. */
    @Query("SELECT s.employee.unit.id, MAX(s.lastUpdatedAt) FROM Submission s GROUP BY s.employee.unit.id")
    List<Object[]> findMaxLastUpdatedAtGroupedByUnit();
}
