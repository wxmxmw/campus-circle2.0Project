package com.campus.campus_backend.repository.governance;

import com.campus.campus_backend.domain.governance.GovernanceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GovernanceSnapshotRepository extends JpaRepository<GovernanceSnapshot, Long> {
    List<GovernanceSnapshot> findByEventIdOrderByVersionNoDesc(Long eventId);

    Optional<GovernanceSnapshot> findTopByEventIdOrderByVersionNoDesc(Long eventId);
}
