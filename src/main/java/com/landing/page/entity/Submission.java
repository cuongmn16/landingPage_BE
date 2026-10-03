package com.landing.page.entity;

import com.landing.page.entity.enums.SubmissionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "submissions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_employee_mission", columnNames = {"employee_id", "mission_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Column(name = "long_text_answer", columnDefinition = "TEXT")
    private String longTextAnswer;

    @Column(name = "selected_options_json", columnDefinition = "TEXT")
    private String selectedOptionsJson;

    @Column(name = "ordered_data_json", columnDefinition = "TEXT")
    private String orderedDataJson;

    @Column(name = "evidence_source", columnDefinition = "TEXT")
    private String evidenceSource;

    @Column(name = "witness_name", length = 200)
    private String witnessName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    @Column(name = "score")
    private Double score;

    @Column(name = "submission_time", nullable = false)
    private LocalDateTime submissionTime;

    @Column(name = "last_updated_at", nullable = false)
    private LocalDateTime lastUpdatedAt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SubmissionAttachment> attachments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.submissionTime == null) {
            this.submissionTime = now;
        }
        this.lastUpdatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastUpdatedAt = LocalDateTime.now();
    }

    public void addAttachment(SubmissionAttachment attachment) {
        attachments.add(attachment);
        attachment.setSubmission(this);
    }

    public void removeAttachment(SubmissionAttachment attachment) {
        attachments.remove(attachment);
        attachment.setSubmission(null);
    }
}
