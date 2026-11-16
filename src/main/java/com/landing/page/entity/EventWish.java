package com.landing.page.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_wishes")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventWish {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_name", nullable = false, length = 100)
    private String senderName;

    @Column(name = "unit_name", nullable = false, length = 150)
    private String unitName;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "is_hidden", nullable = false)
    @Builder.Default
    private Boolean isHidden = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.isHidden == null) {
            this.isHidden = false;
        }
    }
}
