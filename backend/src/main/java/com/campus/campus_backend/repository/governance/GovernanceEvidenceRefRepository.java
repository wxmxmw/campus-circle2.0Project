package com.campus.campus_backend.repository.governance;

import com.campus.campus_backend.domain.governance.GovernanceEvidenceRef;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GovernanceEvidenceRefRepository extends JpaRepository<GovernanceEvidenceRef, Long> {
    List<GovernanceEvidenceRef> findByEventIdOrderByCreatedAtDesc(Long eventId);

    @Query("""
            select ref
            from GovernanceEvidenceRef ref
            join ref.event event
            where (:eventId is null or event.id = :eventId)
              and (
                :keyword is null
                or ref.title like concat('%', :keyword, '%')
                or ref.excerpt like concat('%', :keyword, '%')
                or event.title like concat('%', :keyword, '%')
                or event.eventCode like concat('%', :keyword, '%')
              )
            order by ref.createdAt desc, ref.id desc
            """)
    Page<GovernanceEvidenceRef> searchOfficialEvidence(@Param("eventId") Long eventId,
                                                       @Param("keyword") String keyword,
                                                       Pageable pageable);

    void deleteByEventId(Long eventId);
}
