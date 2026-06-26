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

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "governance_actions", indexes = {
        @Index(name = "idx_governance_action_event", columnList = "event_id"),
        @Index(name = "idx_governance_action_operator", columnList = "operatorUserId")
})
public class GovernanceAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private GovernanceEvent event;

    @Column(nullable = false, length = 32)
    private String actionType;

    @Column(columnDefinition = "text")
    private String actionNote;

    @Column
    private Long operatorUserId;

    @Column(nullable = false)
    private Boolean fromAgent = false;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
