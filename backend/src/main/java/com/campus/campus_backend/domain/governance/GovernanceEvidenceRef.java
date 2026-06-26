package com.campus.campus_backend.domain.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "governance_evidence_refs", indexes = {
        @Index(name = "idx_governance_evidence_event", columnList = "event_id")
})
public class GovernanceEvidenceRef {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private GovernanceEvent event;

    @Column(nullable = false, length = 32)
    private String evidenceType;

    @Column
    private Long refId;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String excerpt;

    @Column(precision = 5, scale = 2)
    private BigDecimal confidence;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
