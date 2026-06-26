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
@Table(name = "governance_event_sources", uniqueConstraints = {
        @UniqueConstraint(name = "uk_governance_event_source", columnNames = {"event_id", "sourceType", "sourceId"})
}, indexes = {
        @Index(name = "idx_governance_event_source_event", columnList = "event_id"),
        @Index(name = "idx_governance_event_source_lookup", columnList = "sourceType,sourceId")
})
public class GovernanceEventSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private GovernanceEvent event;

    @Column(nullable = false, length = 32)
    private String sourceType;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private Integer weight = 1;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
