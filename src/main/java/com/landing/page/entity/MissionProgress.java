package com.landing.page.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bản nháp tiến trình chơi của một nhân viên cho một Mission
 * (lưu trên server để chơi tiếp được trên máy khác).
 */
@Entity
@Table(
    name = "mission_progress",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_progress_employee_mission", columnNames = {"employee_id", "mission_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissionProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Column(name = "progress_json", columnDefinition = "TEXT", nullable = false)
    private String progressJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
