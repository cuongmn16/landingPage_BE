package com.landing.page.repository;

import com.landing.page.entity.EventWish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventWishRepository extends JpaRepository<EventWish, Long> {

    List<EventWish> findTop200ByIsHiddenFalseOrderByCreatedAtDesc();

    long countByIsHiddenFalse();

    List<EventWish> findAllByOrderByCreatedAtDesc();
}
