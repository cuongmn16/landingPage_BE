package com.landing.page.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "gratitude_letters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GratitudeLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "salutation", length = 20)
    @Builder.Default
    private String salutation = "Anh/Chị";

    @Column(name = "unit_name", length = 150)
    private String unitName;

    @Column(name = "days_of_service", nullable = false)
    private Integer daysOfService;

    @Column(name = "years_desc", length = 255)
    private String yearsDesc;

    @Column(name = "is_opened", nullable = false)
    @Builder.Default
    private Boolean isOpened = false;

    @Column(name = "is_published")
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.isOpened == null) {
            this.isOpened = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
