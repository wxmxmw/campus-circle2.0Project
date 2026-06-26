package com.campus.campus_backend.repository.governance;

import com.campus.campus_backend.domain.governance.GovernanceSignal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GovernanceSignalRepository extends JpaRepository<GovernanceSignal, Long> {
    boolean existsBySignalTypeAndSourceId(String signalType, Long sourceId);

    List<GovernanceSignal> findByIngestStatusOrderBySignalTimeAsc(String ingestStatus);

    List<GovernanceSignal> findByEventIdOrderBySignalTimeAsc(Long eventId);

    long countByEventId(Long eventId);

    long countByEventIdAndSignalType(Long eventId, String signalType);
}
