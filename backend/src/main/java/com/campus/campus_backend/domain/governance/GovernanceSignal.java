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
@Table(name = "governance_signals", uniqueConstraints = {
        @UniqueConstraint(name = "uk_governance_signal_type_source", columnNames = {"signalType", "sourceId"})
}, indexes = {
        @Index(name = "idx_governance_signal_event", columnList = "event_id"),
        @Index(name = "idx_governance_signal_type_time", columnList = "signalType,signalTime"),
        @Index(name = "idx_governance_signal_ingest_status", columnList = "ingestStatus")
})
public class GovernanceSignal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String signalType;

    @Column(nullable = false)
    private Long sourceId;

    @Column
    private Long sourceUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private GovernanceEvent event;

    @Column(nullable = false, columnDefinition = "text")
    private String contentText;

    @Column(columnDefinition = "json")
    private String metadataJson;

    @Column(nullable = false)
    private LocalDateTime signalTime;

    @Column(nullable = false, length = 32)
    private String ingestStatus = "NEW";

    @CreationTimestamp
    private LocalDateTime createdAt;
}
