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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "governance_snapshots", uniqueConstraints = {
        @UniqueConstraint(name = "uk_governance_snapshot_version", columnNames = {"event_id", "versionNo"})
}, indexes = {
        @Index(name = "idx_governance_snapshot_event", columnList = "event_id")
})
public class GovernanceSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private GovernanceEvent event;

    @Column(nullable = false)
    private Integer versionNo;

    @Column(columnDefinition = "text")
    private String opinionSummary;

    @Column(columnDefinition = "text")
    private String demandsSummary;

    @Column(columnDefinition = "json")
    private String situationResultJson;

    @Column(columnDefinition = "json")
    private String rumorResultJson;

    @Column(columnDefinition = "json")
    private String suggestionResultJson;

    @Column(columnDefinition = "json")
    private String modelTraceJson;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
