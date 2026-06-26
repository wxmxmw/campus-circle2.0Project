package com.campus.campus_backend.repository.governance;

import com.campus.campus_backend.domain.governance.GovernanceEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GovernanceEventRepository extends JpaRepository<GovernanceEvent, Long> {
    @Query("""
            select e from GovernanceEvent e
            where (:status is null or e.status = :status)
              and (:riskLevel is null or e.riskLevel = :riskLevel)
              and (:rumorStatus is null or e.rumorStatus = :rumorStatus)
            order by e.lastActiveAt desc nulls last, e.createdAt desc, e.id desc
            """)
    Page<GovernanceEvent> search(@Param("status") String status,
                                 @Param("riskLevel") String riskLevel,
                                 @Param("rumorStatus") String rumorStatus,
                                 Pageable pageable);

    @Query("""
            select e from GovernanceEvent e
            where e.status <> 'CLOSED'
              and e.lastActiveAt >= :since
            order by e.lastActiveAt desc, e.createdAt desc
            """)
    List<GovernanceEvent> findActiveSince(@Param("since") LocalDateTime since);
}
