package com.campus.campus_backend.repository.governance;

import com.campus.campus_backend.domain.governance.GovernanceAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GovernanceActionRepository extends JpaRepository<GovernanceAction, Long> {
    List<GovernanceAction> findByEventIdOrderByCreatedAtDesc(Long eventId);
}
