package com.campus.campus_backend.domain.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "governance_events", indexes = {
        @Index(name = "idx_governance_event_status", columnList = "status"),
        @Index(name = "idx_governance_event_risk_level", columnList = "riskLevel"),
        @Index(name = "idx_governance_event_last_active", columnList = "lastActiveAt")
})
public class GovernanceEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String eventCode;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(nullable = false, length = 32)
    private String status = "OPEN";

    @Column(nullable = false, length = 16)
    private String riskLevel = "LOW";

    @Column(nullable = false)
    private Integer riskScore = 0;

    @Column(nullable = false)
    private Integer clusterVersion = 1;

    @Column(nullable = false)
    private Integer sourceCount = 0;

    @Column(nullable = false)
    private Integer reportCount = 0;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal negativeRatio = BigDecimal.ZERO;

    @Column(nullable = false, length = 32)
    private String rumorStatus = "UNVERIFIED";

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime lastActiveAt;

    @Column
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
