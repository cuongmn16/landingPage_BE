package com.landing.page.repository;

import com.landing.page.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {
    Optional<Unit> findByCode(String code);
    Optional<Unit> findByName(String name);

    /** sort_order NULL xếp sau cùng (mặc định của PostgreSQL với ASC). */
    List<Unit> findAllByOrderBySortOrderAscIdAsc();

    @Query("SELECT COALESCE(MAX(u.sortOrder), 0) FROM Unit u")
    int findMaxSortOrder();
}
