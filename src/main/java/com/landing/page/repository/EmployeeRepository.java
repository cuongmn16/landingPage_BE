package com.landing.page.repository;

import com.landing.page.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByEmployeeCode(String employeeCode);
    Optional<Employee> findByEmail(String email);
    List<Employee> findByUnitId(Long unitId);
    long countByUnitId(Long unitId);

    @Query("SELECT e FROM Employee e WHERE e.unit.id = :unitId AND e.isEmailVerified = true")
    List<Employee> findVerifiedEmployeesByUnitId(@Param("unitId") Long unitId);
}
