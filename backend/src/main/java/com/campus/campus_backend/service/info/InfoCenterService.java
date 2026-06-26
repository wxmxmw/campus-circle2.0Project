package com.campus.campus_backend.service.info;

import com.campus.campus_backend.common.BizException;
import com.campus.campus_backend.common.ErrorCode;
import com.campus.campus_backend.domain.*;
import com.campus.campus_backend.dto.info.CreateNoticeCommentRequest;
import com.campus.campus_backend.dto.info.ManualNoticeRequest;
import com.campus.campus_backend.dto.info.UpdateSubscriptionKeywordsRequest;
import com.campus.campus_backend.dto.info.UpsertInfoSourceRequest;
import com.campus.campus_backend.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InfoCenterService {
    private final SubscriptionSourceRepository subscriptionSourceRepository;
    private final NoticeSubscriptionRepository noticeSubscriptionRepository;
    private final AggregatedNoticeRepository aggregatedNoticeRepository;
    private final NoticeCommentRepository noticeCommentRepository;
    private final NoticeCommentLikeRepository noticeCommentLikeRepository;
    private final SourceFetchLogRepository sourceFetchLogRepository;
    private final UserRepository userRepository;
    private final NoticeIngestionService noticeIngestionService;
    private final List<NoticeFetcher> fetchers;
    private final ObjectMapper objectMapper;

    public InfoCenterService(SubscriptionSourceRepository subscriptionSourceRepository,
            NoticeSubscriptionRepository noticeSubscriptionRepository,
            AggregatedNoticeRepository aggregatedNoticeRepository,
            NoticeCommentRepository noticeCommentRepository,
            NoticeCommentLikeRepository noticeCommentLikeRepository,
            SourceFetchLogRepository sourceFetchLogRepository,
            UserRepository userRepository,
            NoticeIngestionService noticeIngestionService,
            List<NoticeFetcher> fetchers,
            ObjectMapper objectMapper) {
        this.subscriptionSourceRepository = subscriptionSourceRepository;
        this.noticeSubscriptionRepository = noticeSubscriptionRepository;
        this.aggregatedNoticeRepository = aggregatedNoticeRepository;
        this.noticeCommentRepository = noticeCommentRepository;
        this.noticeCommentLikeRepository = noticeCommentLikeRepository;
        this.sourceFetchLogRepository = sourceFetchLogRepository;
        this.userRepository = userRepository;
        this.noticeIngestionService = noticeIngestionService;
        this.fetchers = fetchers;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listSources(String keyword, User currentUser) {
        List<SubscriptionSource> sources = (keyword == null || keyword.isBlank())
                ? subscriptionSourceRepository.findAll()
                : subscriptionSourceRepository.findByNameContainingIgnoreCaseOrSearchKeywordsContainingIgnoreCaseOrderByUpdatedAtDesc(keyword, keyword);
        Set<Long> subscribedIds = currentUser == null ? Set.of()
                : noticeSubscriptionRepository.findByUserIdAndEnabledTrue(currentUser.getId()).stream()
                        .map(subscription -> subscription.getSource().getId())
                        .collect(Collectors.toSet());
        return sources.stream()
                .filter(source -> !CanvasBindingService.PERSONAL_CANVAS_SOURCE_KEY.equals(source.getSourceKey()))
                .map(source -> toSourceMap(source, subscribedIds.contains(source.getId())))
                .toList();
    }

    public Map<String, Object> getSourceDetail(Long sourceId) {
        SubscriptionSource source = requireSource(sourceId);
        Map<String, Object> map = toSourceMap(source, false);
        map.put("fetchConfigJson", source.getFetchConfigJson());
        return map;
    }

    @Transactional
    public Map<String, Object> subscribe(Long sourceId, User user) {
        SubscriptionSource source = requireSource(sourceId);
        NoticeSubscription subscription = noticeSubscriptionRepository.findByUserIdAndSourceId(user.getId(), sourceId)
                .orElseGet(NoticeSubscription::new);
        subscription.setUser(user);
        subscription.setSource(source);
        subscription.setEnabled(true);
        if (subscription.getKeywordRulesJson() == null) {
            subscription.setKeywordRulesJson("[]");
        }
        noticeSubscriptionRepository.save(subscription);
        return Map.of("sourceId", sourceId, "enabled", true);
    }

    @Transactional
    public Map<String, Object> unsubscribe(Long sourceId, User user) {
        NoticeSubscription subscription = noticeSubscriptionRepository.findByUserIdAndSourceId(user.getId(), sourceId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        subscription.setEnabled(false);
        noticeSubscriptionRepository.save(subscription);
        return Map.of("sourceId", sourceId, "enabled", false);
    }

    @Transactional
    public Map<String, Object> updateSubscriptionKeywords(Long sourceId, User user,
            UpdateSubscriptionKeywordsRequest request) {
        NoticeSubscription subscription = noticeSubscriptionRepository.findByUserIdAndSourceId(user.getId(), sourceId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        try {
            subscription.setKeywordRulesJson(objectMapper.writeValueAsString(
                    request.getKeywords() == null ? List.of() : request.getKeywords()));
        } catch (Exception e) {
            subscription.setKeywordRulesJson("[]");
        }
        noticeSubscriptionRepository.save(subscription);
        return Map.of("sourceId", sourceId, "keywords", parseJsonList(subscription.getKeywordRulesJson()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listSubscriptions(User user) {
        List<Map<String, Object>> list = noticeSubscriptionRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(subscription -> {
                    Map<String, Object> item = toSourceMap(subscription.getSource(), Boolean.TRUE.equals(subscription.getEnabled()));
                    item.put("keywords", parseJsonList(subscription.getKeywordRulesJson()));
                    item.put("subscriptionId", subscription.getId());
                    return item;
                }).toList();
        return Map.of("list", list, "total", list.size());
    }

    public Map<String, Object> listNotices(User user, String category, Long sourceId, String keyword,
            Boolean onlySubscribed, int page, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.max(pageSize, 1));
        List<Long> subscribedIds = user == null ? List.of()
                : noticeSubscriptionRepository.findByUserIdAndEnabledTrue(user.getId()).stream()
                        .map(item -> item.getSource().getId()).toList();
        String normalizedCategory = category == null || category.isBlank() ? null : category.trim();
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        org.springframework.data.domain.Page<AggregatedNotice> noticePage;
        if (user == null) {
            if (Boolean.TRUE.equals(onlySubscribed)) {
                noticePage = org.springframework.data.domain.Page.empty(pageable);
            } else {
                noticePage = aggregatedNoticeRepository.searchPublicNotices("ONLINE", normalizedCategory, sourceId,
                        normalizedKeyword, pageable);
            }
        } else if (Boolean.TRUE.equals(onlySubscribed) && !subscribedIds.isEmpty()) {
            List<AggregatedNotice> subscribedPage = aggregatedNoticeRepository
                    .findSubscribedAndOwnedVisibleNotices("ONLINE", subscribedIds, user.getId(), pageable)
                    .getContent()
                    .stream()
                    .filter(notice -> normalizedCategory == null || normalizedCategory.equalsIgnoreCase(notice.getCategory()))
                    .filter(notice -> sourceId == null || Objects.equals(sourceId, notice.getSource().getId()))
                    .filter(notice -> normalizedKeyword == null
                            || containsIgnoreCase(notice.getTitle(), normalizedKeyword)
                            || containsIgnoreCase(notice.getSummary(), normalizedKeyword)
                            || containsIgnoreCase(notice.getContentSnapshot(), normalizedKeyword))
                    .toList();
            noticePage = new org.springframework.data.domain.PageImpl<>(subscribedPage, pageable, subscribedPage.size());
        } else {
            noticePage = aggregatedNoticeRepository.searchVisibleNotices("ONLINE", user.getId(), normalizedCategory,
                    sourceId, normalizedKeyword, pageable);
        }
        List<Map<String, Object>> filtered = noticePage.getContent().stream()
                .map(notice -> toNoticeMap(notice, user, subscribedIds))
                .toList();
        return Map.of(
                "list", filtered,
                "page", page,
                "pageSize", pageSize,
                "total", noticePage.getTotalElements(),
                "hasMore", noticePage.hasNext());
    }

    public Map<String, Object> listNoticeCategories(User user) {
        List<String> categories = user == null
                ? aggregatedNoticeRepository.findPublicCategories("ONLINE")
                : aggregatedNoticeRepository.findVisibleCategories("ONLINE", user.getId());
        return Map.of("list", categories);
    }

    public Map<String, Object> getNoticeDetail(Long id, User user) {
        AggregatedNotice notice = aggregatedNoticeRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        ensureNoticeVisible(notice, user);
        List<Long> subscribedIds = user == null ? List.of()
                : noticeSubscriptionRepository.findByUserIdAndEnabledTrue(user.getId()).stream()
                        .map(item -> item.getSource().getId()).toList();
        Map<String, Object> data = toNoticeMap(notice, user, subscribedIds);
        data.put("contentSnapshot", notice.getContentSnapshot());
        data.put("rawPayload", parseJsonObject(notice.getRawPayloadJson()));
        return data;
    }

    public Map<String, Object> listNoticeComments(Long noticeId, User user) {
        ensureNoticeVisible(requireNotice(noticeId), user);
        List<Map<String, Object>> list = noticeCommentRepository.findByNoticeIdAndParentIdIsNullOrderByCreatedAtAsc(noticeId)
                .stream()
                .map(comment -> toCommentMap(comment, user, true))
                .toList();
        return Map.of("list", list, "total", list.size());
    }

    @Transactional
    public Map<String, Object> createNoticeComment(Long noticeId, User user, CreateNoticeCommentRequest request) {
        AggregatedNotice notice = requireNotice(noticeId);
        ensureNoticeVisible(notice, user);
        NoticeComment comment = new NoticeComment();
        comment.setNotice(notice);
        comment.setUser(user);
        comment.setParentId(request.getParentId());
        if (request.getReplyToCommentId() != null) {
            NoticeComment reply = noticeCommentRepository.findById(request.getReplyToCommentId())
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
            comment.setReplyToUser(reply.getUser());
        }
        comment.setContent(request.getContent());
        comment.setIsAnonymous(Boolean.TRUE.equals(request.getAnonymous()));
        noticeCommentRepository.save(comment);
        return toCommentMap(comment, user, false);
    }

    @Transactional
    public Map<String, Object> likeNoticeComment(Long commentId, User user) {
        NoticeComment comment = noticeCommentRepository.findById(commentId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        boolean existed = noticeCommentLikeRepository.existsByCommentIdAndUserId(commentId, user.getId());
        if (!existed) {
            NoticeCommentLike like = new NoticeCommentLike();
            like.setComment(comment);
            like.setUser(user);
            noticeCommentLikeRepository.save(like);
        }
        comment.setLikeCount((int) noticeCommentLikeRepository.countByCommentId(commentId));
        noticeCommentRepository.save(comment);
        return Map.of("commentId", commentId, "isLiked", true, "likeCount", comment.getLikeCount());
    }

    @Transactional
    public void deleteNoticeComment(Long commentId, User user) {
        NoticeComment comment = noticeCommentRepository.findById(commentId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());
        if (!isAdmin && !Objects.equals(comment.getUser().getId(), user.getId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        noticeCommentRepository.findByParentIdOrderByCreatedAtAsc(commentId)
                .forEach(noticeCommentRepository::delete);
        noticeCommentRepository.delete(comment);
    }

    @Transactional
    public Map<String, Object> saveSource(Long id, UpsertInfoSourceRequest request) {
        SubscriptionSource source = id == null ? new SubscriptionSource()
                : subscriptionSourceRepository.findById(id).orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        source.setName(request.getName());
        source.setType(request.getType());
        source.setSourceKey(request.getSourceKey());
        source.setSourceUrl(request.getSourceUrl());
        source.setSearchKeywords(request.getSearchKeywords());
        source.setFetchStrategy(request.getFetchStrategy());
        source.setFetchConfigJson(request.getFetchConfigJson());
        source.setStatus(request.getStatus() == null || request.getStatus().isBlank() ? "ACTIVE" : request.getStatus());
        subscriptionSourceRepository.save(source);
        return toSourceMap(source, false);
    }

    @Transactional
    public Map<String, Object> toggleSourceStatus(Long id, String status) {
        SubscriptionSource source = requireSource(id);
        source.setStatus(status);
        subscriptionSourceRepository.save(source);
        return Map.of("id", id, "status", status);
    }

    @Transactional
    public Map<String, Object> manualNotice(Long id, ManualNoticeRequest request) {
        SubscriptionSource source = requireSource(request.getSourceId() != null ? request.getSourceId() : id);
        String externalId = "manual-" + (id != null ? id : System.currentTimeMillis());
        AggregatedNotice notice = noticeIngestionService.saveManualNotice(source, externalId, request.getTitle(),
                request.getSummary(), request.getCategory(), request.getOriginalUrl(), parseDateTime(request.getPublishTime()),
                parseDateTime(request.getDeadline()), request.getTargetAudience(), request.getLocation(),
                request.getContentSnapshot(), arrayToList(request.getTags()), toActionLinks(request.getActionLinks()));
        return toNoticeMap(notice, null, List.of());
    }

    @Transactional
    public Map<String, Object> updateManualNotice(Long noticeId, ManualNoticeRequest request) {
        AggregatedNotice existing = requireNotice(noticeId);
        AggregatedNotice notice = noticeIngestionService.saveManualNotice(existing.getSource(), existing.getExternalId(),
                request.getTitle(), request.getSummary(), request.getCategory(), request.getOriginalUrl(),
                parseDateTime(request.getPublishTime()), parseDateTime(request.getDeadline()), request.getTargetAudience(),
                request.getLocation(), request.getContentSnapshot(), arrayToList(request.getTags()),
                toActionLinks(request.getActionLinks()));
        return toNoticeMap(notice, null, List.of());
    }

    @Transactional
    public Map<String, Object> offlineNotice(Long noticeId) {
        AggregatedNotice notice = requireNotice(noticeId);
        notice.setStatus("OFFLINE");
        aggregatedNoticeRepository.save(notice);
        return Map.of("id", noticeId, "status", "OFFLINE");
    }

    @Transactional
    public Map<String, Object> fetchSource(Long sourceId) {
        SubscriptionSource source = requireSource(sourceId);
        NoticeFetcher fetcher = fetchers.stream().filter(item -> item.supports(source)).findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.BAD_REQUEST.getCode(), "No fetcher for source"));
        return noticeIngestionService.ingest(source, fetcher.fetch(source));
    }

    public Map<String, Object> fetchLogs(Long sourceId) {
        List<Map<String, Object>> list = sourceFetchLogRepository.findTop20BySourceIdOrderByCreatedAtDesc(sourceId).stream()
                .map(log -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", log.getId());
                    item.put("sourceId", log.getSource().getId());
                    item.put("status", log.getStatus());
                    item.put("successCount", log.getSuccessCount());
                    item.put("failureCount", log.getFailureCount());
                    item.put("errorMessage", log.getErrorMessage());
                    item.put("createdAt", toIso(log.getCreatedAt()));
                    return item;
                })
                .toList();
        return Map.of("list", list, "total", list.size());
    }

    public List<SubscriptionSource> listActiveSources() {
        return subscriptionSourceRepository.findByStatusOrderByUpdatedAtDesc("ACTIVE").stream()
                .filter(source -> !CanvasBindingService.PERSONAL_CANVAS_SOURCE_KEY.equals(source.getSourceKey()))
                .toList();
    }

    @Transactional
    public Map<String, Object> purgeMockData() {
        int deletedLogs = 0;
        int deletedNoticesBySource = 0;
        int deletedNoticesByPayload = 0;
        int deletedSubscriptions = 0;
        int deletedSources = 0;

        deletedSubscriptions = noticeSubscriptionRepository.deleteByMockSources();
        deletedLogs = sourceFetchLogRepository.deleteByMockSources();
        deletedNoticesBySource = aggregatedNoticeRepository.deleteByMockSources();
        deletedNoticesByPayload = aggregatedNoticeRepository.deleteByRawPayloadLike("\"mock\":true");

        List<SubscriptionSource> mockSources = subscriptionSourceRepository.findAll().stream()
                .filter(source -> source.getFetchStrategy() != null
                        && source.getFetchStrategy().toUpperCase().startsWith("MOCK"))
                .toList();
        subscriptionSourceRepository.deleteAll(mockSources);
        deletedSources = mockSources.size();

        return Map.of(
                "deletedSubscriptions", deletedSubscriptions,
                "deletedLogs", deletedLogs,
                "deletedNoticesBySource", deletedNoticesBySource,
                "deletedNoticesByPayload", deletedNoticesByPayload,
                "deletedSources", deletedSources);
    }

    private SubscriptionSource requireSource(Long sourceId) {
        return subscriptionSourceRepository.findById(sourceId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
    }

    private AggregatedNotice requireNotice(Long id) {
        return aggregatedNoticeRepository.findById(id).orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
    }

    private void ensureNoticeVisible(AggregatedNotice notice, User user) {
        if (notice.getOwnerUser() == null) {
            return;
        }
        if (user == null || !Objects.equals(notice.getOwnerUser().getId(), user.getId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private Map<String, Object> toSourceMap(SubscriptionSource source, boolean subscribed) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", source.getId());
        map.put("name", source.getName());
        map.put("type", source.getType());
        map.put("sourceKey", source.getSourceKey());
        map.put("sourceUrl", source.getSourceUrl());
        map.put("searchKeywords", source.getSearchKeywords());
        map.put("fetchStrategy", source.getFetchStrategy());
        map.put("status", source.getStatus());
        map.put("lastFetchedAt", toIso(source.getLastFetchedAt()));
        map.put("lastFetchStatus", source.getLastFetchStatus());
        map.put("subscribed", subscribed);
        return map;
    }

    private Map<String, Object> toNoticeMap(AggregatedNotice notice, User user, List<Long> subscribedIds) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", notice.getId());
        map.put("sourceId", notice.getSource().getId());
        map.put("sourceName", notice.getSourceName());
        map.put("title", notice.getTitle());
        map.put("summary", notice.getSummary());
        map.put("category", notice.getCategory());
        map.put("publishTime", toIso(notice.getPublishTime()));
        map.put("deadline", toIso(notice.getDeadline()));
        map.put("targetAudience", notice.getTargetAudience());
        map.put("location", notice.getLocation());
        map.put("actionLinks", parseJsonObjectList(notice.getActionLinksJson()));
        map.put("tags", parseJsonList(notice.getTagsJson()));
        map.put("originalUrl", notice.getOriginalUrl());
        map.put("status", notice.getStatus());
        map.put("onlySubscribedMatched", subscribedIds.contains(notice.getSource().getId()));
        map.put("privateNotice", notice.getOwnerUser() != null);
        return map;
    }

    private Map<String, Object> toCommentMap(NoticeComment comment, User currentUser, boolean includeReplies) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", comment.getId());
        item.put("noticeId", comment.getNotice().getId());
        item.put("userId", comment.getUser().getId());
        item.put("username", Boolean.TRUE.equals(comment.getIsAnonymous()) ? "匿名用户"
                : defaultName(comment.getUser()));
        item.put("avatar", Boolean.TRUE.equals(comment.getIsAnonymous()) ? null : comment.getUser().getAvatarUrl());
        item.put("content", comment.getContent());
        item.put("time", toIso(comment.getCreatedAt()));
        item.put("likes", comment.getLikeCount());
        item.put("isLiked", currentUser != null
                && noticeCommentLikeRepository.existsByCommentIdAndUserId(comment.getId(), currentUser.getId()));
        item.put("replyTo", comment.getReplyToUser() == null ? null : defaultName(comment.getReplyToUser()));
        item.put("isMine", currentUser != null && Objects.equals(comment.getUser().getId(), currentUser.getId()));
        if (includeReplies) {
            item.put("replies", noticeCommentRepository.findByParentIdOrderByCreatedAtAsc(comment.getId()).stream()
                    .map(reply -> toCommentMap(reply, currentUser, false))
                    .toList());
        }
        return item;
    }

    private String defaultName(User user) {
        return user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getUsername();
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword.toLowerCase());
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception ignore) {
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignore) {
        }
        try {
            return LocalDateTime.parse(value.replace(" ", "T"));
        } catch (Exception ignore) {
        }
        return null;
    }

    private List<String> arrayToList(String[] items) {
        return items == null ? List.of() : Arrays.stream(items).filter(Objects::nonNull).toList();
    }

    private List<Map<String, String>> toActionLinks(String[] links) {
        if (links == null) {
            return List.of();
        }
        List<Map<String, String>> list = new ArrayList<>();
        for (String link : links) {
            if (link != null && !link.isBlank()) {
                list.add(Map.of("label", "相关链接", "url", link));
            }
        }
        return list;
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> parseJsonObjectList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toIso(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
