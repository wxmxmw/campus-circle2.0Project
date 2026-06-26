package com.campus.campus_backend.repository.governance;

import com.campus.campus_backend.domain.governance.GovernanceEventSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GovernanceEventSourceRepository extends JpaRepository<GovernanceEventSource, Long> {
    Optional<GovernanceEventSource> findFirstBySourceTypeAndSourceIdOrderByCreatedAtDesc(String sourceType, Long sourceId);

    boolean existsByEventIdAndSourceTypeAndSourceId(Long eventId, String sourceType, Long sourceId);

    List<GovernanceEventSource> findByEventIdOrderByCreatedAtAsc(Long eventId);
}
