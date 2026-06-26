package com.campus.campus_backend.service.governance;

import com.campus.campus_backend.common.BizException;
import com.campus.campus_backend.common.ErrorCode;
import com.campus.campus_backend.domain.governance.GovernanceAction;
import com.campus.campus_backend.domain.governance.GovernanceEvent;
import com.campus.campus_backend.domain.governance.GovernanceEvidenceRef;
import com.campus.campus_backend.domain.governance.GovernanceSignal;
import com.campus.campus_backend.domain.governance.GovernanceSnapshot;
import com.campus.campus_backend.dto.governance.GovernanceEventActionRequest;
import com.campus.campus_backend.dto.governance.GovernanceMergeRequest;
import com.campus.campus_backend.dto.governance.GovernanceMonitorRunRequest;
import com.campus.campus_backend.repository.governance.GovernanceActionRepository;
import com.campus.campus_backend.repository.governance.GovernanceEventRepository;
import com.campus.campus_backend.repository.governance.GovernanceEventSourceRepository;
import com.campus.campus_backend.repository.governance.GovernanceEvidenceRefRepository;
import com.campus.campus_backend.repository.governance.GovernanceSignalRepository;
import com.campus.campus_backend.repository.governance.GovernanceSnapshotRepository;
import com.campus.campus_backend.service.governance.agent.EventClusteringAgent;
import com.campus.campus_backend.service.governance.agent.GovernanceAgentContext;
import com.campus.campus_backend.service.governance.agent.GovernanceSuggestionAgent;
import com.campus.campus_backend.service.governance.agent.PostmortemAgent;
import com.campus.campus_backend.service.governance.agent.RumorVerificationAgent;
import com.campus.campus_backend.service.governance.agent.SituationAnalysisAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class GovernanceOrchestratorService {
    private static final Logger log = LoggerFactory.getLogger(GovernanceOrchestratorService.class);
    private static final Set<String> LOCAL_EVENT_LOCKS = ConcurrentHashMap.newKeySet();
    private static final String STAGE_INGEST = "ingest";
    private static final String STAGE_CLUSTER = "cluster";
    private static final String AGENT_SITUATION = "situation";
    private static final String AGENT_RUMOR = "rumor";
    private static final String AGENT_SUGGESTION = "suggestion";

    private final GovernanceSignalIngestService governanceSignalIngestService;
    private final GovernanceSignalRepository governanceSignalRepository;
    private final GovernanceEventRepository governanceEventRepository;
    private final GovernanceSnapshotRepository governanceSnapshotRepository;
    private final GovernanceActionRepository governanceActionRepository;
    private final GovernanceEventSourceRepository governanceEventSourceRepository;
    private final GovernanceEvidenceRefRepository governanceEvidenceRefRepository;
    private final GovernanceTextSupport textSupport;
    private final EventClusteringAgent eventClusteringAgent;
    private final SituationAnalysisAgent situationAnalysisAgent;
    private final RumorVerificationAgent rumorVerificationAgent;
    private final GovernanceSuggestionAgent governanceSuggestionAgent;
    private final PostmortemAgent postmortemAgent;
    private final GovernanceAiSupportService governanceAiSupportService;
    private final StringRedisTemplate stringRedisTemplate;
    private final boolean redisEnabled;
    private final TransactionTemplate transactionTemplate;
    private final Map<String, MonitorRunState> monitorRuns = new ConcurrentHashMap<>();
    private final ExecutorService monitorExecutor = Executors.newCachedThreadPool();

    public GovernanceOrchestratorService(GovernanceSignalIngestService governanceSignalIngestService,
                                         GovernanceSignalRepository governanceSignalRepository,
                                         GovernanceEventRepository governanceEventRepository,
                                         GovernanceSnapshotRepository governanceSnapshotRepository,
                                         GovernanceActionRepository governanceActionRepository,
                                         GovernanceEventSourceRepository governanceEventSourceRepository,
                                         GovernanceEvidenceRefRepository governanceEvidenceRefRepository,
                                         GovernanceTextSupport textSupport,
                                         EventClusteringAgent eventClusteringAgent,
                                         SituationAnalysisAgent situationAnalysisAgent,
                                         RumorVerificationAgent rumorVerificationAgent,
                                         GovernanceSuggestionAgent governanceSuggestionAgent,
                                         GovernanceAiSupportService governanceAiSupportService,
                                         PostmortemAgent postmortemAgent,
                                         TransactionTemplate transactionTemplate,
                                         Optional<StringRedisTemplate> stringRedisTemplate,
                                         @Value("${app.redis.enabled:false}") boolean redisEnabled) {
        this.governanceSignalIngestService = governanceSignalIngestService;
        this.governanceSignalRepository = governanceSignalRepository;
        this.governanceEventRepository = governanceEventRepository;
        this.governanceSnapshotRepository = governanceSnapshotRepository;
        this.governanceActionRepository = governanceActionRepository;
        this.governanceEventSourceRepository = governanceEventSourceRepository;
        this.governanceEvidenceRefRepository = governanceEvidenceRefRepository;
        this.textSupport = textSupport;
        this.eventClusteringAgent = eventClusteringAgent;
        this.situationAnalysisAgent = situationAnalysisAgent;
        this.rumorVerificationAgent = rumorVerificationAgent;
        this.governanceSuggestionAgent = governanceSuggestionAgent;
        this.governanceAiSupportService = governanceAiSupportService;
        this.postmortemAgent = postmortemAgent;
        this.transactionTemplate = transactionTemplate;
        this.stringRedisTemplate = redisEnabled ? stringRedisTemplate.orElse(null) : null;
        this.redisEnabled = redisEnabled;
    }

    public Map<String, Object> startMonitor(GovernanceMonitorRunRequest request) {
        GovernanceMonitorRunRequest normalizedRequest = request == null ? new GovernanceMonitorRunRequest() : request;
        String runId = UUID.randomUUID().toString().replace("-", "");
        MonitorRunState state = new MonitorRunState(runId, normalizedRequest);
        monitorRuns.put(runId, state);
        CompletableFuture.runAsync(() -> executeMonitorAsync(runId, normalizedRequest), monitorExecutor);
        return sanitizeResponseMap(state.startPayload());
    }

    public Map<String, Object> getMonitorRun(String runId) {
        MonitorRunState state = monitorRuns.get(runId);
        if (state == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "未找到对应的监测任务");
        }
        return sanitizeResponseMap(state.snapshot());
    }

    public Map<String, Object> runMonitor(GovernanceMonitorRunRequest request) {
        return executeMonitor(request == null ? new GovernanceMonitorRunRequest() : request, null);
    }

    private void executeMonitorAsync(String runId, GovernanceMonitorRunRequest request) {
        MonitorRunState state = monitorRuns.get(runId);
        if (state == null) {
            return;
        }
        state.markRunning("正在启动监测任务");
        try {
            Map<String, Object> result = executeMonitor(request, state);
            state.complete(result);
        } catch (Exception ex) {
            log.error("governance monitor run {} failed", runId, ex);
            state.fail(ex.getMessage() == null || ex.getMessage().isBlank() ? "监测任务执行失败" : ex.getMessage());
        }
    }

    private Map<String, Object> executeMonitor(GovernanceMonitorRunRequest request, MonitorRunState state) {
        if (state != null) {
            state.startStage(STAGE_INGEST, "正在采集社区信号");
        }
        Map<String, Object> ingestResult = governanceSignalIngestService.ingest(request);
        if (state != null) {
            state.completeStage(STAGE_INGEST, buildIngestStageOutput(ingestResult), "信号采集完成");
        }

        List<Long> signalIds = readLongList(ingestResult.get("signalIds"));
        int windowHours = request.getWindowHours() == null ? 24 : request.getWindowHours();

        if (signalIds.isEmpty()) {
            List<Long> activeEventIds = governanceEventRepository.findActiveSince(LocalDateTime.now().minusHours(windowHours))
                    .stream()
                    .map(GovernanceEvent::getId)
                    .toList();
            if (state != null) {
                state.skipStage(STAGE_CLUSTER, "本轮没有新增信号，直接重分析活跃事件", Map.of(
                        "activeEventCount", activeEventIds.size(),
                        "affectedEventIds", activeEventIds
                ));
            }

            Map<String, Object> result = new LinkedHashMap<>(ingestResult);
            List<Map<String, Object>> workflowRuns = new ArrayList<>();
            for (Long eventId : activeEventIds) {
                workflowRuns.add(reanalyzeEventTransactional(eventId, "MONITOR_RUN", windowHours, state));
            }
            result.put("affectedEventIds", activeEventIds);
            result.put("reanalyzedCount", workflowRuns.size());
            result.put("workflowRuns", workflowRuns);
            return sanitizeResponseMap(result);
        }

        List<GovernanceSignal> signals = governanceSignalRepository.findAllById(signalIds);
        GovernanceAgentContext clusterContext = new GovernanceAgentContext();
        clusterContext.setTriggerSource("MONITOR_RUN");
        clusterContext.setWindowHours(windowHours);
        clusterContext.setAiEnabled(governanceAiSupportService.isAvailable());

        if (state != null) {
            state.startStage(STAGE_CLUSTER, "正在将信号聚类到治理事件");
        }
        List<Long> eventIds = eventClusteringAgent.execute(signals, clusterContext);
        if (state != null) {
            state.completeStage(STAGE_CLUSTER, buildClusterStageOutput(clusterContext, eventIds), "事件聚类完成");
        }

        int reanalyzedCount = 0;
        List<Map<String, Object>> workflowRuns = new ArrayList<>();
        for (Long eventId : eventIds) {
            workflowRuns.add(reanalyzeEventTransactional(eventId, "MONITOR_RUN", request.getWindowHours(), state));
            reanalyzedCount++;
        }

        Map<String, Object> result = new LinkedHashMap<>(ingestResult);
        result.put("affectedEventIds", eventIds);
        result.put("reanalyzedCount", reanalyzedCount);
        result.put("clusterTrace", sanitizeResponseMap(clusterContext.getTrace()));
        result.put("workflowRuns", workflowRuns);
        return sanitizeResponseMap(result);
    }

    public Map<String, Object> reanalyzeEvent(Long eventId) {
        return sanitizeResponseMap(reanalyzeEventTransactional(eventId, "MANUAL_REANALYZE", null, null));
    }

    @Transactional
    public Map<String, Object> clearGovernanceData() {
        if (hasActiveMonitorRun()) {
            throw new BizException(ErrorCode.CONFLICT.getCode(), "当前存在正在执行的社区监测任务，请等待完成后再清空事件");
        }

        long actionCount = governanceActionRepository.count();
        long evidenceCount = governanceEvidenceRefRepository.count();
        long snapshotCount = governanceSnapshotRepository.count();
        long eventSourceCount = governanceEventSourceRepository.count();
        long signalCount = governanceSignalRepository.count();
        long eventCount = governanceEventRepository.count();

        governanceActionRepository.deleteAllInBatch();
        governanceEvidenceRefRepository.deleteAllInBatch();
        governanceSnapshotRepository.deleteAllInBatch();
        governanceEventSourceRepository.deleteAllInBatch();
        governanceSignalRepository.deleteAllInBatch();
        governanceEventRepository.deleteAllInBatch();
        monitorRuns.clear();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clearedEventCount", eventCount);
        result.put("clearedSignalCount", signalCount);
        result.put("clearedSnapshotCount", snapshotCount);
        result.put("clearedActionCount", actionCount);
        result.put("clearedEvidenceCount", evidenceCount);
        result.put("clearedEventSourceCount", eventSourceCount);
        return result;
    }

    @Transactional
    public Map<String, Object> recordAction(Long eventId, Long operatorUserId, GovernanceEventActionRequest request) {
        GovernanceEvent event = requireEvent(eventId);
        GovernanceAction action = new GovernanceAction();
        action.setEvent(event);
        action.setActionType(request.getActionType() == null || request.getActionType().isBlank()
                ? "MANUAL_REVIEW" : request.getActionType().trim());
        action.setActionNote(request.getActionNote());
        action.setOperatorUserId(operatorUserId);
        action.setFromAgent(Boolean.TRUE.equals(request.getFromAgent()));
        governanceActionRepository.save(action);

        if (request.getOfficialTitle() != null && !request.getOfficialTitle().isBlank()) {
            GovernanceEvidenceRef ref = new GovernanceEvidenceRef();
            ref.setEvent(event);
            ref.setEvidenceType("ADMIN_NOTE");
            ref.setTitle(request.getOfficialTitle().trim());
            ref.setExcerpt(textSupport.abbreviate(request.getOfficialContent(), 200));
            ref.setConfidence(BigDecimal.valueOf(95));
            governanceEvidenceRefRepository.save(ref);
        }

        if ("ESCALATE_ADMIN".equals(action.getActionType())) {
            event.setStatus("ESCALATED");
        } else if ("OBSERVE".equals(action.getActionType()) && !"CLOSED".equals(event.getStatus())) {
            event.setStatus("OBSERVING");
        } else if (!"CLOSED".equals(event.getStatus())) {
            event.setStatus("ACTION_PENDING");
        }
        governanceEventRepository.save(event);

        return Map.of("actionId", action.getId(), "status", event.getStatus());
    }

    @Transactional
    public Map<String, Object> closeEvent(Long eventId, Long operatorUserId, String note) {
        GovernanceEvent event = requireEvent(eventId);
        event.setStatus("CLOSED");
        event.setResolvedAt(LocalDateTime.now());
        governanceEventRepository.save(event);

        GovernanceAction action = new GovernanceAction();
        action.setEvent(event);
        action.setActionType("CLOSE");
        action.setActionNote(note == null || note.isBlank() ? "管理员手动关闭事件" : note);
        action.setOperatorUserId(operatorUserId);
        governanceActionRepository.save(action);

        return generatePostmortem(eventId);
    }

    @Transactional
    public Map<String, Object> mergeEvents(GovernanceMergeRequest request, Long operatorUserId) {
        if (request.getSourceEventId() == null || request.getTargetEventId() == null
                || request.getSourceEventId().equals(request.getTargetEventId())) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "合并参数不合法");
        }

        GovernanceEvent source = requireEvent(request.getSourceEventId());
        GovernanceEvent target = requireEvent(request.getTargetEventId());

        List<GovernanceSignal> signals = governanceSignalRepository.findByEventIdOrderBySignalTimeAsc(source.getId());
        signals.forEach(signal -> signal.setEvent(target));
        governanceSignalRepository.saveAll(signals);

        governanceEventSourceRepository.findByEventIdOrderByCreatedAtAsc(source.getId()).forEach(sourceRef -> {
            if (!governanceEventSourceRepository.existsByEventIdAndSourceTypeAndSourceId(
                    target.getId(), sourceRef.getSourceType(), sourceRef.getSourceId())) {
                sourceRef.setEvent(target);
                governanceEventSourceRepository.save(sourceRef);
            }
        });

        List<GovernanceAction> actions = governanceActionRepository.findByEventIdOrderByCreatedAtDesc(source.getId());
        actions.forEach(action -> action.setEvent(target));
        governanceActionRepository.saveAll(actions);

        List<GovernanceEvidenceRef> evidenceRefs = governanceEvidenceRefRepository.findByEventIdOrderByCreatedAtDesc(source.getId());
        evidenceRefs.forEach(ref -> ref.setEvent(target));
        governanceEvidenceRefRepository.saveAll(evidenceRefs);

        source.setStatus("CLOSED");
        source.setResolvedAt(LocalDateTime.now());
        source.setSummary(textSupport.abbreviate(
                textSupport.normalizeText(Objects.toString(source.getSummary(), "")) + " 已合并入事件 #" + target.getId(), 200));
        governanceEventRepository.save(source);

        GovernanceAction mergeAction = new GovernanceAction();
        mergeAction.setEvent(target);
        mergeAction.setActionType("MERGE_EVENT");
        mergeAction.setActionNote("事件 #" + source.getId() + " 已合并到当前事件");
        mergeAction.setOperatorUserId(operatorUserId);
        governanceActionRepository.save(mergeAction);

        return reanalyzeEventTransactional(target.getId(), "MERGE", null, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> generatePostmortem(Long eventId) {
        GovernanceEvent event = requireEvent(eventId);
        GovernanceAgentContext context = new GovernanceAgentContext();
        context.setEventId(eventId);
        context.setTriggerSource("POSTMORTEM");
        return postmortemAgent.execute(event, context);
    }

    private Map<String, Object> reanalyzeEventTransactional(Long eventId, String triggerSource, Integer windowHours,
                                                            MonitorRunState state) {
        return transactionTemplate.execute(status -> reanalyzeEventInternal(eventId, triggerSource, windowHours, state));
    }

    private Map<String, Object> reanalyzeEventInternal(Long eventId, String triggerSource, Integer windowHours,
                                                       MonitorRunState state) {
        String lockKey = "governance:run:lock:" + eventId;
        boolean locked = acquireEventLock(lockKey);
        if (!locked) {
            throw new BizException(ErrorCode.CONFLICT.getCode(), "该事件正在分析中，请稍后重试");
        }
        try {
            GovernanceEvent event = requireEvent(eventId);
            GovernanceAgentContext context = new GovernanceAgentContext();
            context.setEventId(eventId);
            context.setTriggerSource(triggerSource);
            context.setWindowHours(windowHours);
            context.setSnapshotVersion(nextSnapshotVersion(eventId));
            context.setAiEnabled(governanceAiSupportService.isAvailable());

            if (state != null) {
                state.beginWorkflow(event.getId(), event.getTitle(), context.getSnapshotVersion());
                state.startWorkflowAgent(event.getId(), AGENT_SITUATION, "态势分析 Agent", "判断风险等级与舆情趋势");
            }
            Map<String, Object> situation = situationAnalysisAgent.execute(event, context);
            context.putTrace("situation", situation);
            if (state != null) {
                state.completeWorkflowAgent(event.getId(), AGENT_SITUATION, summarizeSituation(situation), situation);
                state.startWorkflowAgent(event.getId(), AGENT_RUMOR, "谣言核验 Agent", "核验事实性说法");
            }

            Map<String, Object> rumor = rumorVerificationAgent.execute(event, context);
            context.putTrace("rumor", rumor);
            if (state != null) {
                state.completeWorkflowAgent(event.getId(), AGENT_RUMOR, summarizeRumor(rumor), rumor);
                state.startWorkflowAgent(event.getId(), AGENT_SUGGESTION, "治理建议 Agent", "生成治理动作建议");
            }

            Map<String, Object> suggestion = governanceSuggestionAgent.execute(event, context);
            context.putTrace("suggestion", suggestion);
            if (state != null) {
                state.completeWorkflowAgent(event.getId(), AGENT_SUGGESTION, summarizeSuggestion(suggestion), suggestion);
            }

            event.setSummary(String.valueOf(situation.getOrDefault("opinionSummary", event.getSummary())));
            event.setRiskLevel(String.valueOf(situation.getOrDefault("riskLevel", event.getRiskLevel())));
            event.setRiskScore(intValue(situation.get("riskScore"), event.getRiskScore() == null ? 0 : event.getRiskScore()));
            event.setNegativeRatio(decimalValue(situation.get("negativeRatio"), event.getNegativeRatio()));
            event.setRumorStatus(String.valueOf(rumor.getOrDefault("rumorStatus", event.getRumorStatus())));
            event.setSourceCount((int) governanceSignalRepository.countByEventId(eventId));
            event.setReportCount((int) governanceSignalRepository.countByEventIdAndSignalType(eventId, "REPORT"));
            event.setLastActiveAt(LocalDateTime.now());
            event.setStatus(resolveStatus(event.getStatus(), event.getRiskLevel()));
            governanceEventRepository.save(event);

            GovernanceSnapshot snapshot = new GovernanceSnapshot();
            snapshot.setEvent(event);
            snapshot.setVersionNo(context.getSnapshotVersion());
            snapshot.setOpinionSummary(String.valueOf(situation.getOrDefault("opinionSummary", "")));
            snapshot.setDemandsSummary(String.valueOf(situation.getOrDefault("demandsSummary", "")));
            snapshot.setSituationResultJson(textSupport.toJson(situation));
            snapshot.setRumorResultJson(textSupport.toJson(rumor));
            snapshot.setSuggestionResultJson(textSupport.toJson(suggestion));
            snapshot.setModelTraceJson(textSupport.toJson(buildTrace(context)));
            governanceSnapshotRepository.save(snapshot);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("eventId", event.getId());
            result.put("title", event.getTitle());
            result.put("snapshotVersion", snapshot.getVersionNo());
            result.put("riskLevel", event.getRiskLevel());
            result.put("rumorStatus", event.getRumorStatus());
            result.put("status", event.getStatus());
            result.put("trace", buildTrace(context));
            result.put("agents", buildWorkflowAgents(situation, rumor, suggestion));
            if (state != null) {
                state.completeWorkflow(event.getId(), result);
            }
            return result;
        } finally {
            releaseEventLock(lockKey);
        }
    }

    private GovernanceEvent requireEvent(Long eventId) {
        return governanceEventRepository.findById(eventId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
    }

    private int nextSnapshotVersion(Long eventId) {
        return governanceSnapshotRepository.findTopByEventIdOrderByVersionNoDesc(eventId)
                .map(snapshot -> snapshot.getVersionNo() + 1)
                .orElse(1);
    }

    private String resolveStatus(String currentStatus, String riskLevel) {
        if ("CLOSED".equals(currentStatus)) {
            return currentStatus;
        }
        if ("HIGH".equals(riskLevel)) {
            return "ESCALATED";
        }
        if ("MEDIUM".equals(riskLevel)) {
            return "ACTION_PENDING";
        }
        return "OBSERVING";
    }

    private Map<String, Object> buildTrace(GovernanceAgentContext context) {
        Map<String, Object> trace = new LinkedHashMap<>(context.getTrace());
        trace.put("eventId", context.getEventId());
        trace.put("triggerSource", context.getTriggerSource());
        trace.put("snapshotVersion", context.getSnapshotVersion());
        trace.put("windowHours", context.getWindowHours());
        trace.put("startedAt", String.valueOf(context.getStartedAt()));
        return sanitizeResponseMap(trace);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeResponseMap(Map<String, Object> source) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (source == null) {
            return sanitized;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            sanitized.put(entry.getKey(), sanitizeResponseValue(entry.getValue()));
        }
        return sanitized;
    }

    private Object sanitizeResponseValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof LocalDateTime time) {
            return String.valueOf(time);
        }
        if (value instanceof GovernanceEvent event) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", event.getId());
            info.put("eventCode", event.getEventCode());
            info.put("title", event.getTitle());
            info.put("status", event.getStatus());
            return info;
        }
        if (value instanceof GovernanceSnapshot snapshot) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", snapshot.getId());
            info.put("versionNo", snapshot.getVersionNo());
            info.put("createdAt", String.valueOf(snapshot.getCreatedAt()));
            return info;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sanitized.put(String.valueOf(entry.getKey()), sanitizeResponseValue(entry.getValue()));
            }
            return sanitized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sanitizeResponseValue).collect(Collectors.toList());
        }
        if (value instanceof Set<?> set) {
            return set.stream().map(this::sanitizeResponseValue).collect(Collectors.toList());
        }
        if (value.getClass().getName().startsWith("com.campus.campus_backend.domain.")) {
            return value.getClass().getSimpleName();
        }
        return value.getClass().getSimpleName();
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private BigDecimal decimalValue(Object value, BigDecimal fallback) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text);
            } catch (Exception ignored) {
            }
        }
        return fallback == null ? BigDecimal.ZERO : fallback;
    }

    private List<Long> readLongList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                ids.add(number.longValue());
            }
        }
        return ids;
    }

    private boolean acquireEventLock(String lockKey) {
        if (!redisEnabled || stringRedisTemplate == null) {
            return LOCAL_EVENT_LOCKS.add(lockKey);
        }
        try {
            Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(1));
            return Boolean.TRUE.equals(success);
        } catch (Exception ex) {
            log.warn("governance event lock degraded: {}", ex.getMessage());
            return LOCAL_EVENT_LOCKS.add(lockKey);
        }
    }

    private void releaseEventLock(String lockKey) {
        if (!redisEnabled || stringRedisTemplate == null) {
            LOCAL_EVENT_LOCKS.remove(lockKey);
            return;
        }
        try {
            stringRedisTemplate.delete(lockKey);
        } catch (Exception ex) {
            log.warn("governance event unlock degraded: {}", ex.getMessage());
        } finally {
            LOCAL_EVENT_LOCKS.remove(lockKey);
        }
    }

    private boolean hasActiveMonitorRun() {
        return monitorRuns.values().stream().anyMatch(run ->
                "PENDING".equals(run.status) || "RUNNING".equals(run.status));
    }

    private Map<String, Object> buildIngestStageOutput(Map<String, Object> ingestResult) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("windowHours", ingestResult.get("windowHours"));
        output.put("scannedSignalCount", ingestResult.get("scannedSignalCount"));
        output.put("newSignalCount", ingestResult.get("newSignalCount"));
        output.put("postSignals", ingestResult.get("postSignals"));
        output.put("commentSignals", ingestResult.get("commentSignals"));
        output.put("reportSignals", ingestResult.get("reportSignals"));
        output.put("since", ingestResult.get("since"));
        return output;
    }

    private Map<String, Object> buildClusterStageOutput(GovernanceAgentContext clusterContext, List<Long> eventIds) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("clusteredSignalCount", clusterContext.getTrace().get("clusteredSignalCount"));
        output.put("candidateClusterCount", clusterContext.getTrace().get("candidateClusterCount"));
        output.put("shortlistedClusterCount", clusterContext.getTrace().get("shortlistedClusterCount"));
        output.put("mergedGroupCount", clusterContext.getTrace().get("mergedGroupCount"));
        output.put("selectedClusterCount", clusterContext.getTrace().get("selectedClusterCount"));
        output.put("createdEventCount", clusterContext.getTrace().get("createdEventCount"));
        output.put("updatedEventCount", clusterContext.getTrace().get("updatedEventCount"));
        output.put("discardedSignalCount", clusterContext.getTrace().get("discardedSignalCount"));
        output.put("discardedClusterCount", clusterContext.getTrace().get("discardedClusterCount"));
        output.put("clusterAiUsed", clusterContext.getTrace().get("clusterAiUsed"));
        output.put("clusterMergeAiUsed", clusterContext.getTrace().get("clusterMergeAiUsed"));
        output.put("clusterFinalizeAiUsed", clusterContext.getTrace().get("clusterFinalizeAiUsed"));
        output.put("clusterDecisionMode", clusterContext.getTrace().get("clusterDecisionMode"));
        output.put("affectedEventIds", eventIds);
        output.put("affectedEventCount", eventIds.size());
        return output;
    }

    private String summarizeSituation(Map<String, Object> situation) {
        return String.valueOf(situation.getOrDefault("opinionSummary", "已完成态势分析"));
    }

    private String summarizeRumor(Map<String, Object> rumor) {
        return String.valueOf(rumor.getOrDefault("conclusion", "已完成谣言核验"));
    }

    private String summarizeSuggestion(Map<String, Object> suggestion) {
        return String.valueOf(suggestion.getOrDefault("recommendedMessage", "已生成治理建议"));
    }

    private List<Map<String, Object>> buildWorkflowAgents(Map<String, Object> situation,
                                                          Map<String, Object> rumor,
                                                          Map<String, Object> suggestion) {
        List<Map<String, Object>> agents = new ArrayList<>();
        agents.add(agentSnapshot(AGENT_SITUATION, "态势分析 Agent", "判断风险等级与舆情趋势",
                summarizeSituation(situation), situation));
        agents.add(agentSnapshot(AGENT_RUMOR, "谣言核验 Agent", "核验事实性说法",
                summarizeRumor(rumor), rumor));
        agents.add(agentSnapshot(AGENT_SUGGESTION, "治理建议 Agent", "生成治理动作建议",
                summarizeSuggestion(suggestion), suggestion));
        return agents;
    }

    private Map<String, Object> agentSnapshot(String key, String label, String role, String summary,
                                              Map<String, Object> output) {
        Map<String, Object> agent = new LinkedHashMap<>();
        agent.put("key", key);
        agent.put("label", label);
        agent.put("role", role);
        agent.put("status", "COMPLETED");
        agent.put("summary", summary);
        agent.put("output", sanitizeResponseMap(output));
        return agent;
    }

    private static final class MonitorRunState {
        private final String runId;
        private final LocalDateTime startedAt = LocalDateTime.now();
        private final Map<String, Object> request;
        private final List<Map<String, Object>> stages = new ArrayList<>();
        private final List<Map<String, Object>> workflowRuns = new ArrayList<>();
        private String status = "PENDING";
        private String message = "监测任务已创建";
        private LocalDateTime finishedAt;
        private Map<String, Object> result = new LinkedHashMap<>();

        private MonitorRunState(String runId, GovernanceMonitorRunRequest request) {
            this.runId = runId;
            this.request = new LinkedHashMap<>();
            this.request.put("windowHours", request.getWindowHours());
            this.request.put("includePosts", request.getIncludePosts());
            this.request.put("includeComments", request.getIncludeComments());
            this.request.put("includeReports", request.getIncludeReports());
            this.request.put("maxSignals", request.getMaxSignals());
            this.stages.add(stage(STAGE_INGEST, "信号采集 Agent"));
            this.stages.add(stage(STAGE_CLUSTER, "事件聚类 Agent"));
        }

        private static Map<String, Object> stage(String key, String label) {
            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("key", key);
            stage.put("label", label);
            stage.put("status", "PENDING");
            stage.put("message", "等待执行");
            stage.put("output", Collections.emptyMap());
            return stage;
        }

        private synchronized Map<String, Object> startPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("runId", runId);
            payload.put("status", status);
            payload.put("message", message);
            payload.put("startedAt", String.valueOf(startedAt));
            return payload;
        }

        private synchronized void markRunning(String message) {
            this.status = "RUNNING";
            this.message = message;
        }

        private synchronized void startStage(String key, String message) {
            this.status = "RUNNING";
            this.message = message;
            Map<String, Object> stage = findStage(key);
            stage.put("status", "RUNNING");
            stage.put("message", message);
        }

        private synchronized void completeStage(String key, Map<String, Object> output, String message) {
            this.status = "RUNNING";
            this.message = message;
            Map<String, Object> stage = findStage(key);
            stage.put("status", "COMPLETED");
            stage.put("message", message);
            stage.put("output", output);
        }

        private synchronized void skipStage(String key, String message, Map<String, Object> output) {
            this.status = "RUNNING";
            this.message = message;
            Map<String, Object> stage = findStage(key);
            stage.put("status", "SKIPPED");
            stage.put("message", message);
            stage.put("output", output);
        }

        private synchronized void beginWorkflow(Long eventId, String title, Integer snapshotVersion) {
            Map<String, Object> workflow = findWorkflow(eventId);
            workflow.put("eventId", eventId);
            workflow.put("title", title);
            workflow.put("snapshotVersion", snapshotVersion);
            workflow.put("status", "RUNNING");
            this.message = "正在分析事件 #" + eventId;
        }

        @SuppressWarnings("unchecked")
        private synchronized void startWorkflowAgent(Long eventId, String key, String label, String role) {
            Map<String, Object> workflow = findWorkflow(eventId);
            List<Map<String, Object>> agents = (List<Map<String, Object>>) workflow.get("agents");
            Map<String, Object> agent = agents.stream()
                    .filter(item -> Objects.equals(item.get("key"), key))
                    .findFirst()
                    .orElseGet(() -> {
                        Map<String, Object> created = new LinkedHashMap<>();
                        created.put("key", key);
                        created.put("label", label);
                        created.put("role", role);
                        created.put("status", "PENDING");
                        created.put("summary", "");
                        created.put("output", Collections.emptyMap());
                        agents.add(created);
                        return created;
                    });
            agent.put("status", "RUNNING");
            workflow.put("status", "RUNNING");
            this.message = label + "执行中";
        }

        @SuppressWarnings("unchecked")
        private synchronized void completeWorkflowAgent(Long eventId, String key, String summary, Map<String, Object> output) {
            Map<String, Object> workflow = findWorkflow(eventId);
            List<Map<String, Object>> agents = (List<Map<String, Object>>) workflow.get("agents");
            for (Map<String, Object> agent : agents) {
                if (Objects.equals(agent.get("key"), key)) {
                    agent.put("status", "COMPLETED");
                    agent.put("summary", summary);
                    agent.put("output", output);
                    return;
                }
            }
        }

        private synchronized void completeWorkflow(Long eventId, Map<String, Object> result) {
            Map<String, Object> workflow = findWorkflow(eventId);
            workflow.putAll(result);
            workflow.put("status", "COMPLETED");
            this.message = "事件 #" + eventId + " 分析完成";
        }

        private synchronized void complete(Map<String, Object> result) {
            this.status = "COMPLETED";
            this.message = "监测任务执行完成";
            this.finishedAt = LocalDateTime.now();
            this.result = result;
        }

        private synchronized void fail(String message) {
            this.status = "FAILED";
            this.message = message;
            this.finishedAt = LocalDateTime.now();
            for (Map<String, Object> stage : stages) {
                if (Objects.equals(stage.get("status"), "RUNNING")) {
                    stage.put("status", "FAILED");
                    stage.put("message", message);
                }
            }
            for (Map<String, Object> workflow : workflowRuns) {
                if (Objects.equals(workflow.get("status"), "RUNNING")) {
                    workflow.put("status", "FAILED");
                }
                Object agents = workflow.get("agents");
                if (agents instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> rawAgent) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> agent = (Map<String, Object>) rawAgent;
                            if (Objects.equals(agent.get("status"), "RUNNING")) {
                                agent.put("status", "FAILED");
                                agent.put("summary", message);
                            }
                        }
                    }
                }
            }
        }

        private synchronized Map<String, Object> snapshot() {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("runId", runId);
            snapshot.put("status", status);
            snapshot.put("message", message);
            snapshot.put("startedAt", String.valueOf(startedAt));
            snapshot.put("finishedAt", finishedAt == null ? null : String.valueOf(finishedAt));
            snapshot.put("request", new LinkedHashMap<>(request));
            snapshot.put("stages", new ArrayList<>(stages));
            snapshot.put("workflowRuns", new ArrayList<>(workflowRuns));
            snapshot.put("result", new LinkedHashMap<>(result));
            return snapshot;
        }

        private Map<String, Object> findStage(String key) {
            return stages.stream()
                    .filter(item -> Objects.equals(item.get("key"), key))
                    .findFirst()
                    .orElseThrow();
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> findWorkflow(Long eventId) {
            return workflowRuns.stream()
                    .filter(item -> Objects.equals(item.get("eventId"), eventId))
                    .findFirst()
                    .orElseGet(() -> {
                        Map<String, Object> workflow = new LinkedHashMap<>();
                        workflow.put("eventId", eventId);
                        workflow.put("status", "PENDING");
                        workflow.put("agents", new ArrayList<Map<String, Object>>());
                        workflowRuns.add(workflow);
                        return workflow;
                    });
        }
    }
}
