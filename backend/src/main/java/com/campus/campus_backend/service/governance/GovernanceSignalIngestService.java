package com.campus.campus_backend.service.governance;

import com.campus.campus_backend.common.BizException;
import com.campus.campus_backend.common.ErrorCode;
import com.campus.campus_backend.domain.Comment;
import com.campus.campus_backend.domain.Post;
import com.campus.campus_backend.domain.Report;
import com.campus.campus_backend.domain.User;
import com.campus.campus_backend.domain.governance.GovernanceSignal;
import com.campus.campus_backend.dto.governance.GovernanceMonitorRunRequest;
import com.campus.campus_backend.repository.CommentRepository;
import com.campus.campus_backend.repository.PostRepository;
import com.campus.campus_backend.repository.ReportRepository;
import com.campus.campus_backend.repository.governance.GovernanceSignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class GovernanceSignalIngestService {
    private static final Logger log = LoggerFactory.getLogger(GovernanceSignalIngestService.class);
    private static final String MONITOR_LOCK_KEY = "governance:monitor:lock";
    private static final AtomicBoolean LOCAL_MONITOR_LOCK = new AtomicBoolean(false);

    private final GovernanceSignalRepository governanceSignalRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final GovernanceTextSupport textSupport;
    private final StringRedisTemplate stringRedisTemplate;
    private final boolean redisEnabled;

    public GovernanceSignalIngestService(GovernanceSignalRepository governanceSignalRepository,
                                         PostRepository postRepository,
                                         CommentRepository commentRepository,
                                         ReportRepository reportRepository,
                                         GovernanceTextSupport textSupport,
                                         Optional<StringRedisTemplate> stringRedisTemplate,
                                         @Value("${app.redis.enabled:false}") boolean redisEnabled) {
        this.governanceSignalRepository = governanceSignalRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.reportRepository = reportRepository;
        this.textSupport = textSupport;
        this.stringRedisTemplate = redisEnabled ? stringRedisTemplate.orElse(null) : null;
        this.redisEnabled = redisEnabled;
    }

    @Transactional
    public Map<String, Object> ingest(GovernanceMonitorRunRequest request) {
        boolean locked = acquireLock();
        if (!locked) {
            throw new BizException(ErrorCode.CONFLICT.getCode(), "治理监测任务正在执行，请稍后重试");
        }

        try {
            int windowHours = normalizeWindowHours(request.getWindowHours());
            int maxSignals = normalizeMaxSignals(request.getMaxSignals());
            LocalDateTime since = LocalDateTime.now().minusHours(windowHours);

            List<GovernanceSignal> candidates = new ArrayList<>();
            int scannedPosts = 0;
            int scannedComments = 0;
            int scannedReports = 0;
            if (!Boolean.FALSE.equals(request.getIncludePosts())) {
                List<Post> posts = postRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since);
                scannedPosts = posts.size();
                for (Post post : posts) {
                    if (governanceSignalRepository.existsBySignalTypeAndSourceId("POST", post.getId())) {
                        continue;
                    }
                    candidates.add(fromPost(post));
                }
            }
            if (!Boolean.FALSE.equals(request.getIncludeComments())) {
                List<Comment> comments = commentRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since);
                scannedComments = comments.size();
                for (Comment comment : comments) {
                    if (governanceSignalRepository.existsBySignalTypeAndSourceId("COMMENT", comment.getId())) {
                        continue;
                    }
                    candidates.add(fromComment(comment));
                }
            }
            if (!Boolean.FALSE.equals(request.getIncludeReports())) {
                List<Report> reports = reportRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since);
                scannedReports = reports.size();
                for (Report report : reports) {
                    if (governanceSignalRepository.existsBySignalTypeAndSourceId("REPORT", report.getId())) {
                        continue;
                    }
                    candidates.add(fromReport(report));
                }
            }

            candidates.sort(Comparator.comparing(GovernanceSignal::getSignalTime).reversed());
            if (candidates.size() > maxSignals) {
                candidates = new ArrayList<>(candidates.subList(0, maxSignals));
            }

            List<GovernanceSignal> saved = governanceSignalRepository.saveAll(candidates);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("windowHours", windowHours);
            result.put("maxSignals", maxSignals);
            result.put("newSignalCount", saved.size());
            result.put("scannedPostCount", scannedPosts);
            result.put("scannedCommentCount", scannedComments);
            result.put("scannedReportCount", scannedReports);
            result.put("scannedSignalCount", scannedPosts + scannedComments + scannedReports);
            result.put("signalIds", saved.stream().map(GovernanceSignal::getId).toList());
            result.put("postSignals", saved.stream().filter(signal -> "POST".equals(signal.getSignalType())).count());
            result.put("commentSignals", saved.stream().filter(signal -> "COMMENT".equals(signal.getSignalType())).count());
            result.put("reportSignals", saved.stream().filter(signal -> "REPORT".equals(signal.getSignalType())).count());
            result.put("since", since.toString());
            return result;
        } finally {
            releaseLock();
        }
    }

    private GovernanceSignal fromPost(Post post) {
        GovernanceSignal signal = new GovernanceSignal();
        signal.setSignalType("POST");
        signal.setSourceId(post.getId());
        signal.setSourceUserId(post.getUser() != null ? post.getUser().getId() : null);
        signal.setSignalTime(post.getCreatedAt());
        signal.setContentText(textSupport.normalizeText(
                Objects.toString(post.getSummary(), "") + " " + Objects.toString(post.getContent(), "")));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("postId", post.getId());
        metadata.put("title", Objects.toString(post.getSummary(), ""));
        metadata.put("category", post.getCategory() != null ? post.getCategory().getName() : null);
        metadata.put("tags", textSupport.readJsonStringList(post.getTags()));
        metadata.put("viewCount", post.getViewCount());
        metadata.put("likeCount", post.getLikeCount());
        metadata.put("commentCount", post.getCommentCount());
        metadata.put("userId", post.getUser() != null ? post.getUser().getId() : null);
        metadata.put("authorName", displayName(post.getUser()));
        signal.setMetadataJson(textSupport.toJson(metadata));
        return signal;
    }

    private GovernanceSignal fromComment(Comment comment) {
        GovernanceSignal signal = new GovernanceSignal();
        signal.setSignalType("COMMENT");
        signal.setSourceId(comment.getId());
        signal.setSourceUserId(comment.getUser() != null ? comment.getUser().getId() : null);
        signal.setSignalTime(comment.getCreatedAt());
        String postTitle = comment.getPost() != null ? Objects.toString(comment.getPost().getSummary(), "") : "";
        signal.setContentText(textSupport.normalizeText(postTitle + " " + Objects.toString(comment.getContent(), "")));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("commentId", comment.getId());
        metadata.put("postId", comment.getPost() != null ? comment.getPost().getId() : null);
        metadata.put("postTitle", postTitle);
        metadata.put("parentId", comment.getParentId());
        metadata.put("likeCount", comment.getLikeCount());
        metadata.put("userId", comment.getUser() != null ? comment.getUser().getId() : null);
        metadata.put("authorName", displayName(comment.getUser()));
        signal.setMetadataJson(textSupport.toJson(metadata));
        return signal;
    }

    private GovernanceSignal fromReport(Report report) {
        GovernanceSignal signal = new GovernanceSignal();
        signal.setSignalType("REPORT");
        signal.setSourceId(report.getId());
        signal.setSourceUserId(report.getReporter() != null ? report.getReporter().getId() : null);
        signal.setSignalTime(report.getCreatedAt());

        String relatedTitle = "";
        String relatedContent = "";
        if (report.getTargetType() != null && report.getTargetType() == 1) {
            postRepository.findById(report.getTargetId()).ifPresent(post -> {
                signal.setContentText(textSupport.normalizeText(
                        Objects.toString(report.getReason(), "") + " " + Objects.toString(post.getSummary(), "")
                                + " " + Objects.toString(post.getContent(), "")));
            });
            Optional<Post> postOptional = postRepository.findById(report.getTargetId());
            if (postOptional.isPresent()) {
                Post post = postOptional.get();
                relatedTitle = Objects.toString(post.getSummary(), "");
                relatedContent = textSupport.abbreviate(post.getContent(), 120);
            }
        }
        if (signal.getContentText() == null || signal.getContentText().isBlank()) {
            signal.setContentText(textSupport.normalizeText(Objects.toString(report.getReason(), "")));
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reportId", report.getId());
        metadata.put("postId", report.getTargetType() != null && report.getTargetType() == 1 ? report.getTargetId() : null);
        metadata.put("reason", report.getReason());
        metadata.put("targetType", report.getTargetType());
        metadata.put("relatedTitle", relatedTitle);
        metadata.put("relatedContent", relatedContent);
        metadata.put("reporterName", displayName(report.getReporter()));
        signal.setMetadataJson(textSupport.toJson(metadata));
        return signal;
    }

    private String displayName(User user) {
        if (user == null) {
            return "匿名用户";
        }
        return user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getUsername();
    }

    private int normalizeWindowHours(Integer value) {
        if (value == null) {
            return 24;
        }
        return Math.max(1, Math.min(336, value));
    }

    private int normalizeMaxSignals(Integer value) {
        if (value == null) {
            return 120;
        }
        return Math.max(10, Math.min(300, value));
    }

    private boolean acquireLock() {
        if (!redisEnabled || stringRedisTemplate == null) {
            return LOCAL_MONITOR_LOCK.compareAndSet(false, true);
        }
        try {
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(MONITOR_LOCK_KEY, "1", Duration.ofMinutes(2));
            return Boolean.TRUE.equals(success);
        } catch (Exception ex) {
            log.warn("governance monitor lock degraded: {}", ex.getMessage());
            return LOCAL_MONITOR_LOCK.compareAndSet(false, true);
        }
    }

    private void releaseLock() {
        if (!redisEnabled || stringRedisTemplate == null) {
            LOCAL_MONITOR_LOCK.set(false);
            return;
        }
        try {
            stringRedisTemplate.delete(MONITOR_LOCK_KEY);
        } catch (Exception ex) {
            log.warn("governance monitor unlock degraded: {}", ex.getMessage());
        } finally {
            LOCAL_MONITOR_LOCK.set(false);
        }
    }
}
