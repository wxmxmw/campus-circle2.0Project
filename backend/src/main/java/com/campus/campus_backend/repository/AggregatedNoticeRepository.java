package com.campus.campus_backend.repository;

import com.campus.campus_backend.domain.AggregatedNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AggregatedNoticeRepository extends JpaRepository<AggregatedNotice, Long> {
    Optional<AggregatedNotice> findBySourceIdAndExternalId(Long sourceId, String externalId);
    Optional<AggregatedNotice> findBySourceIdAndOwnerUserIdAndExternalId(Long sourceId, Long ownerUserId, String externalId);
    Page<AggregatedNotice> findByStatusOrderByPublishTimeDescCreatedAtDesc(String status, Pageable pageable);
    Page<AggregatedNotice> findBySourceIdInAndStatusOrderByPublishTimeDescCreatedAtDesc(Collection<Long> sourceIds, String status, Pageable pageable);
    List<AggregatedNotice> findTop20ByStatusOrderByPublishTimeDescCreatedAtDesc(String status);

    @Query("""
            select n from AggregatedNotice n
            where n.status = :status and n.ownerUser is null
            order by n.publishTime desc, n.createdAt desc
            """)
    Page<AggregatedNotice> findPublicNotices(@Param("status") String status, Pageable pageable);

    @Query("""
            select n from AggregatedNotice n
            where n.status = :status
              and n.ownerUser is null
              and (:category is null or lower(n.category) = lower(:category))
              and (:sourceId is null or n.source.id = :sourceId)
              and (
                :keyword is null
                or lower(n.title) like lower(concat('%', :keyword, '%'))
                or lower(n.summary) like lower(concat('%', :keyword, '%'))
                or lower(n.contentSnapshot) like lower(concat('%', :keyword, '%'))
              )
            order by n.publishTime desc, n.createdAt desc
            """)
    Page<AggregatedNotice> searchPublicNotices(@Param("status") String status,
                                               @Param("category") String category,
                                               @Param("sourceId") Long sourceId,
                                               @Param("keyword") String keyword,
                                               Pageable pageable);

    @Query("""
            select distinct n.category from AggregatedNotice n
            where n.status = :status
              and n.ownerUser is null
              and n.category is not null
              and n.category <> ''
            order by n.category asc
            """)
    List<String> findPublicCategories(@Param("status") String status);

    @Query("""
            select n from AggregatedNotice n
            where n.status = :status and (n.ownerUser is null or n.ownerUser.id = :userId)
            order by n.publishTime desc, n.createdAt desc
            """)
    Page<AggregatedNotice> findVisibleNotices(@Param("status") String status, @Param("userId") Long userId, Pageable pageable);

    @Query("""
            select n from AggregatedNotice n
            where n.status = :status
              and (n.ownerUser is null or n.ownerUser.id = :userId)
              and (:category is null or lower(n.category) = lower(:category))
              and (:sourceId is null or n.source.id = :sourceId)
              and (
                :keyword is null
                or lower(n.title) like lower(concat('%', :keyword, '%'))
                or lower(n.summary) like lower(concat('%', :keyword, '%'))
                or lower(n.contentSnapshot) like lower(concat('%', :keyword, '%'))
              )
            order by n.publishTime desc, n.createdAt desc
            """)
    Page<AggregatedNotice> searchVisibleNotices(@Param("status") String status,
                                                @Param("userId") Long userId,
                                                @Param("category") String category,
                                                @Param("sourceId") Long sourceId,
                                                @Param("keyword") String keyword,
                                                Pageable pageable);

    @Query("""
            select distinct n.category from AggregatedNotice n
            where n.status = :status
              and (n.ownerUser is null or n.ownerUser.id = :userId)
              and n.category is not null
              and n.category <> ''
            order by n.category asc
            """)
    List<String> findVisibleCategories(@Param("status") String status, @Param("userId") Long userId);

    @Query("""
            select n from AggregatedNotice n
            where n.status = :status
              and ((n.ownerUser is null and n.source.id in :sourceIds) or n.ownerUser.id = :userId)
            order by n.publishTime desc, n.createdAt desc
            """)
    Page<AggregatedNotice> findSubscribedAndOwnedVisibleNotices(@Param("status") String status,
            @Param("sourceIds") Collection<Long> sourceIds,
            @Param("userId") Long userId,
            Pageable pageable);

    @Modifying
    @Transactional
    @Query("""
            delete from AggregatedNotice n
            where n.source.id in (
                select s.id from SubscriptionSource s
                where s.fetchStrategy is not null and upper(s.fetchStrategy) like 'MOCK%'
            )
            """)
    int deleteByMockSources();

    @Modifying
    @Transactional
    @Query("""
            delete from AggregatedNotice n
            where n.rawPayloadJson is not null and n.rawPayloadJson like %:needle%
            """)
    int deleteByRawPayloadLike(@Param("needle") String needle);
}
