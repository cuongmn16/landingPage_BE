package com.landing.page.repository;

import com.landing.page.entity.GratitudeLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GratitudeLetterRepository extends JpaRepository<GratitudeLetter, Long> {

    Optional<GratitudeLetter> findByEmailIgnoreCase(String email);

    Optional<GratitudeLetter> findByEmployeeCodeIgnoreCase(String employeeCode);

    Optional<GratitudeLetter> findFirstByFullNameIgnoreCase(String fullName);

    List<GratitudeLetter> findAllByOrderByIdAsc();
}
