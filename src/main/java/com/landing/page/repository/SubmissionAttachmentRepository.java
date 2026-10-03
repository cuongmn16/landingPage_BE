package com.landing.page.repository;

import com.landing.page.entity.SubmissionAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionAttachmentRepository extends JpaRepository<SubmissionAttachment, Long> {
    List<SubmissionAttachment> findBySubmissionId(Long submissionId);
}
