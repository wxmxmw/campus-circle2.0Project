package com.campus.campus_backend.service.governance.agent;

import com.campus.campus_backend.domain.governance.GovernanceEvent;
import com.campus.campus_backend.domain.governance.GovernanceEventSource;
import com.campus.campus_backend.domain.governance.GovernanceSignal;
import com.campus.campus_backend.repository.governance.GovernanceEventRepository;
import com.campus.campus_backend.repository.governance.GovernanceEventSourceRepository;
import com.campus.campus_backend.repository.governance.GovernanceSignalRepository;
import com.campus.campus_backend.service.governance.GovernanceAiSupportService;
import com.campus.campus_backend.service.governance.GovernanceTextSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EventClusteringAgent implements GovernanceAgent<List<GovernanceSignal>, List<Long>> {
    private static final int MAX_FINAL_EVENTS = 5;
    private static final int MAX_AI_CLUSTERS = 12;
    private static final int MAX_EXISTING_EVENTS = 8;
    private static final double CLUSTER_MATCH_THRESHOLD = 0.42;
    private static final double GROUP_MERGE_THRESHOLD = 0.56;
    private static final double EVENT_MATCH_THRESHOLD = 0.58;
    private static final List<String> NON_GOVERNANCE_MARKERS = List.of(
            "出吗", "出 ", "收 ", "求购", "二手", "代写", "写作业", "拼车", "接单", "鼠标", "耳机", "显卡"
    );
    private static final List<String> LOW_INFORMATION_TEXTS = List.of(
            "对啊", "顶顶顶", "还难吃", "6", "666", "dd", "ddd", "帮顶", "有没有", "真的假的"
    );
    private static final List<String> GOVERNANCE_HINTS = List.of(
            "封校", "封楼", "停电", "施工", "噪音", "图书馆", "占座", "教务", "成绩", "举报", "谣言",
            "爆料", "通知", "周末", "宿舍", "食堂", "安全", "泄题", "考试", "网络", "校园网", "维修",
            "返校", "外卖", "校门", "施工扰民"
    );

    private final GovernanceEventRepository governanceEventRepository;
    private final GovernanceEventSourceRepository governanceEventSourceRepository;
    private final GovernanceSignalRepository governanceSignalRepository;
    private final GovernanceTextSupport textSupport;
    private final GovernanceAiSupportService governanceAiSupportService;

    public EventClusteringAgent(GovernanceEventRepository governanceEventRepository,
                                GovernanceEventSourceRepository governanceEventSourceRepository,
                                GovernanceSignalRepository governanceSignalRepository,
                                GovernanceTextSupport textSupport,
                                GovernanceAiSupportService governanceAiSupportService) {
        this.governanceEventRepository = governanceEventRepository;
        this.governanceEventSourceRepository = governanceEventSourceRepository;
        this.governanceSignalRepository = governanceSignalRepository;
        this.textSupport = textSupport;
        this.governanceAiSupportService = governanceAiSupportService;
    }

    @Override
    @Transactional
    public List<Long> execute(List<GovernanceSignal> input, GovernanceAgentContext context) {
        if (input == null || input.isEmpty()) {
            context.putTrace("clusteredSignalCount", 0);
            context.putTrace("affectedEventIds", List.of());
            context.putTrace("candidateClusterCount", 0);
            context.putTrace("selectedClusterCount", 0);
            context.putTrace("discardedSignalCount", 0);
            context.putTrace("clusterAiUsed", false);
            context.putTrace("clusterDecisionMode", "EMPTY");
            return List.of();
        }

        List<GovernanceEvent> activeEvents = governanceEventRepository.findActiveSince(LocalDateTime.now().minusHours(72));
        Map<Long, GovernanceEvent> activeEventMap = activeEvents.stream()
                .collect(Collectors.toMap(GovernanceEvent::getId, item -> item, (left, right) -> left, LinkedHashMap::new));

        List<CandidateCluster> allClusters = buildCandidateClusters(input, activeEvents);
        List<CandidateCluster> shortlisted = shortlistClusters(allClusters);
        MergePhaseResult mergePhase = buildMergedGroups(shortlisted, activeEvents, context);
        FinalizePhaseResult finalizePhase = buildFinalDecisions(mergePhase.groups, activeEvents, context);

        Set<Long> affectedEvents = new LinkedHashSet<>();
        Set<Long> attachedSignalIds = new LinkedHashSet<>();
        int createdEventCount = 0;
        int updatedEventCount = 0;

        for (EventDecision decision : finalizePhase.decisions) {
            GovernanceEvent event = resolveTargetEvent(decision, activeEventMap);
            boolean created = event.getId() == null;
            if (created) {
                event = createEvent(decision.primarySignal(), decision.title, decision.summary);
                activeEventMap.put(event.getId(), event);
                createdEventCount++;
            } else {
                updatedEventCount++;
            }

            refreshEventDraft(event, decision);
            GovernanceEvent savedEvent = governanceEventRepository.save(event);
            for (GovernanceSignal signal : decision.signals()) {
                attachSignal(signal, savedEvent);
                attachedSignalIds.add(signal.getId());
            }
            recalculateEventStats(savedEvent);
            affectedEvents.add(savedEvent.getId());
        }

        List<GovernanceSignal> discardedSignals = input.stream()
                .filter(signal -> !attachedSignalIds.contains(signal.getId()))
                .toList();
        markDiscardedSignals(discardedSignals);

        context.putTrace("clusteredSignalCount", input.size());
        context.putTrace("candidateClusterCount", allClusters.size());
        context.putTrace("shortlistedClusterCount", shortlisted.size());
        context.putTrace("mergedGroupCount", mergePhase.groups.size());
        context.putTrace("selectedClusterCount", finalizePhase.selectedClusterCount);
        context.putTrace("affectedEventIds", new ArrayList<>(affectedEvents));
        context.putTrace("createdEventCount", createdEventCount);
        context.putTrace("updatedEventCount", updatedEventCount);
        context.putTrace("discardedSignalCount", discardedSignals.size());
        context.putTrace("discardedClusterCount", Math.max(0, allClusters.size() - finalizePhase.selectedClusterCount));
        context.putTrace("clusterAiUsed", mergePhase.aiUsed || finalizePhase.aiUsed);
        context.putTrace("clusterDecisionMode", mergePhase.mode + " -> " + finalizePhase.mode);
        context.putTrace("clusterMergeAiUsed", mergePhase.aiUsed);
        context.putTrace("clusterFinalizeAiUsed", finalizePhase.aiUsed);
        return new ArrayList<>(affectedEvents);
    }

    private List<CandidateCluster> buildCandidateClusters(List<GovernanceSignal> signals, List<GovernanceEvent> activeEvents) {
        List<GovernanceSignal> orderedSignals = signals.stream()
                .sorted(Comparator.comparing(GovernanceSignal::getSignalTime))
                .toList();
        List<CandidateCluster> clusters = new ArrayList<>();
        int sequence = 1;
        for (GovernanceSignal signal : orderedSignals) {
            CandidateCluster bestCluster = null;
            double bestScore = 0.0;
            for (CandidateCluster cluster : clusters) {
                double score = similarity(signal, cluster);
                if (score > bestScore) {
                    bestScore = score;
                    bestCluster = cluster;
                }
            }
            if (bestCluster != null && bestScore >= CLUSTER_MATCH_THRESHOLD) {
                bestCluster.addSignal(signal);
                continue;
            }
            clusters.add(new CandidateCluster("c" + sequence++, signal, activeEvents));
        }
        clusters.forEach(CandidateCluster::finalizeDraft);
        return clusters;
    }

    private List<CandidateCluster> shortlistClusters(List<CandidateCluster> clusters) {
        return clusters.stream()
                .filter(cluster -> cluster.ruleScore >= 2 || cluster.reportCount > 0 || cluster.signalCount > 1)
                .sorted(Comparator.comparingInt(CandidateCluster::priorityScore).reversed()
                        .thenComparing(cluster -> cluster.lastSignalTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_AI_CLUSTERS)
                .toList();
    }

    private MergePhaseResult buildMergedGroups(List<CandidateCluster> shortlisted,
                                               List<GovernanceEvent> activeEvents,
                                               GovernanceAgentContext context) {
        if (shortlisted.isEmpty()) {
            return new MergePhaseResult(List.of(), false, "MERGE_EMPTY");
        }

        List<MergedCandidateGroup> groups = ruleBasedMergedGroups(shortlisted);
        boolean aiUsed = false;
        String mode = "MERGE_RULE";
        if (context.isAiEnabled() && governanceAiSupportService.isAvailable()) {
            List<MergedCandidateGroup> aiGroups = aiMergeGroups(shortlisted);
            if (!aiGroups.isEmpty()) {
                groups = aiGroups;
                aiUsed = true;
                mode = "MERGE_AI";
            }
        }
        groups = attachExistingEvents(groups, activeEvents);
        groups = deduplicateGroups(groups);
        return new MergePhaseResult(groups, aiUsed, mode);
    }

    private FinalizePhaseResult buildFinalDecisions(List<MergedCandidateGroup> groups,
                                                    List<GovernanceEvent> activeEvents,
                                                    GovernanceAgentContext context) {
        if (groups.isEmpty()) {
            return new FinalizePhaseResult(List.of(), 0, false, "FINALIZE_EMPTY");
        }

        List<EventDecision> decisions = ruleBasedFinalDecisions(groups, activeEvents);
        boolean aiUsed = false;
        String mode = "FINALIZE_RULE";
        if (context.isAiEnabled() && governanceAiSupportService.isAvailable()) {
            List<EventDecision> aiDecisions = aiFinalizeGroups(groups, activeEvents);
            if (!aiDecisions.isEmpty()) {
                decisions = aiDecisions;
                aiUsed = true;
                mode = "FINALIZE_AI";
            }
        }
        decisions = deduplicateDecisions(decisions, activeEvents);
        int selectedClusterCount = decisions.stream()
                .mapToInt(decision -> decision.groups.stream().mapToInt(group -> group.clusters.size()).sum())
                .sum();
        return new FinalizePhaseResult(decisions, selectedClusterCount, aiUsed, mode);
    }

    private List<MergedCandidateGroup> aiMergeGroups(List<CandidateCluster> shortlisted) {
        List<Map<String, Object>> payload = shortlisted.stream()
                .map(cluster -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("clusterId", cluster.id);
                    item.put("linkedEventId", cluster.linkedEventId);
                    item.put("signalCount", cluster.signalCount);
                    item.put("reportCount", cluster.reportCount);
                    item.put("ruleScore", cluster.ruleScore);
                    item.put("canonicalHint", cluster.canonicalHint());
                    item.put("keywords", cluster.keywords);
                    item.put("governanceHints", cluster.governanceHints);
                    item.put("draftTitle", cluster.draftTitle);
                    item.put("draftSummary", cluster.draftSummary);
                    item.put("representativeTexts", cluster.representativeTexts);
                    item.put("timeRange", Map.of(
                            "start", String.valueOf(cluster.firstSignalTime),
                            "end", String.valueOf(cluster.lastSignalTime)
                    ));
                    return item;
                })
                .toList();

        Map<String, Object> aiResult = governanceAiSupportService.completeJson(
                "You are merging candidate clusters for a campus governance monitoring system. "
                        + "Return JSON only. Use Chinese for reason fields. "
                        + "Your only job is to decide which clusters describe the same real-world governance event. "
                        + "Do not generate final events yet.",
                """
                        Return JSON only with this schema:
                        {
                          "mergeGroups":[
                            {
                              "groupKey":"stable_english_or_pinyin_key",
                              "clusterIds":["c1","c2"],
                              "reason":"Chinese brief reason"
                            }
                          ],
                          "discardedClusterIds":["c9","c10"]
                        }

                        Rules:
                        - Merge clusters that refer to the same governance incident even if the wording differs.
                        - Prefer larger merged groups for semantically identical incidents.
                        - Discard vague fragments, chatter, trading content, or non-governance content.
                        - Every kept cluster should belong to exactly one merge group.
                        - Do not create more than 8 merge groups.

                        Candidate clusters:
                        %s
                        """.formatted(payload));

        Object mergeGroups = aiResult.get("mergeGroups");
        if (!(mergeGroups instanceof List<?> rawGroups) || rawGroups.isEmpty()) {
            return List.of();
        }

        Map<String, CandidateCluster> clusterMap = shortlisted.stream()
                .collect(Collectors.toMap(cluster -> cluster.id, cluster -> cluster, (left, right) -> left, LinkedHashMap::new));
        Set<String> assigned = new LinkedHashSet<>();
        Set<String> discarded = readStringList(aiResult.get("discardedClusterIds")).stream().collect(Collectors.toSet());
        List<MergedCandidateGroup> result = new ArrayList<>();

        for (Object item : rawGroups) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            String groupKey = safeText(rawMap.get("groupKey"));
            List<String> clusterIds = readStringList(rawMap.get("clusterIds"));
            if (clusterIds.isEmpty()) {
                continue;
            }
            List<CandidateCluster> clusters = clusterIds.stream()
                    .filter(clusterMap::containsKey)
                    .filter(clusterId -> !discarded.contains(clusterId))
                    .filter(clusterId -> assigned.add(clusterId))
                    .map(clusterMap::get)
                    .toList();
            if (clusters.isEmpty()) {
                continue;
            }
            result.add(new MergedCandidateGroup(normalizeGroupKey(groupKey, clusters), clusters));
        }

        for (CandidateCluster cluster : shortlisted) {
            if (!assigned.contains(cluster.id) && !discarded.contains(cluster.id)) {
                result.add(new MergedCandidateGroup(normalizeGroupKey("", List.of(cluster)), List.of(cluster)));
            }
        }
        return result;
    }

    private List<MergedCandidateGroup> ruleBasedMergedGroups(List<CandidateCluster> shortlisted) {
        List<MergedCandidateGroup> groups = new ArrayList<>();
        for (CandidateCluster cluster : shortlisted.stream()
                .sorted(Comparator.comparingInt(CandidateCluster::priorityScore).reversed())
                .toList()) {
            MergedCandidateGroup best = null;
            double bestScore = 0.0;
            for (MergedCandidateGroup group : groups) {
                double score = groupSimilarity(group, cluster);
                if (score > bestScore) {
                    bestScore = score;
                    best = group;
                }
            }
            if (best != null && bestScore >= GROUP_MERGE_THRESHOLD) {
                best.addCluster(cluster);
            } else {
                groups.add(new MergedCandidateGroup(normalizeGroupKey("", List.of(cluster)), List.of(cluster)));
            }
        }
        return groups;
    }

    private List<MergedCandidateGroup> attachExistingEvents(List<MergedCandidateGroup> groups, List<GovernanceEvent> activeEvents) {
        for (MergedCandidateGroup group : groups) {
            Set<Long> linkedIds = group.clusters.stream()
                    .map(cluster -> cluster.linkedEventId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!linkedIds.isEmpty()) {
                group.targetEventId = linkedIds.iterator().next();
                continue;
            }
            findBestExistingEvent(group, activeEvents).ifPresent(event -> group.targetEventId = event.getId());
        }
        return groups;
    }

    private List<MergedCandidateGroup> deduplicateGroups(List<MergedCandidateGroup> groups) {
        List<MergedCandidateGroup> deduped = new ArrayList<>();
        for (MergedCandidateGroup group : groups.stream()
                .sorted(Comparator.comparingInt(MergedCandidateGroup::priorityScore).reversed())
                .toList()) {
            MergedCandidateGroup matched = null;
            for (MergedCandidateGroup existing : deduped) {
                if (groupToGroupSimilarity(existing, group) >= GROUP_MERGE_THRESHOLD) {
                    matched = existing;
                    break;
                }
            }
            if (matched == null) {
                deduped.add(group);
            } else {
                matched.merge(group);
            }
        }
        return deduped;
    }

    private List<EventDecision> aiFinalizeGroups(List<MergedCandidateGroup> groups, List<GovernanceEvent> activeEvents) {
        List<Map<String, Object>> groupPayload = groups.stream()
                .sorted(Comparator.comparingInt(MergedCandidateGroup::priorityScore).reversed())
                .limit(Math.max(MAX_FINAL_EVENTS + 3, groups.size()))
                .map(group -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("groupKey", group.groupKey);
                    item.put("targetEventId", group.targetEventId);
                    item.put("signalCount", group.signalCount());
                    item.put("reportCount", group.reportCount());
                    item.put("ruleScore", group.ruleScore());
                    item.put("canonicalHint", group.canonicalHint());
                    item.put("keywords", group.keywords());
                    item.put("governanceHints", group.governanceHints());
                    item.put("draftTitle", group.draftTitle());
                    item.put("draftSummary", group.draftSummary());
                    item.put("representativeTexts", group.representativeTexts());
                    item.put("timeRange", Map.of(
                            "start", String.valueOf(group.firstSignalTime()),
                            "end", String.valueOf(group.lastSignalTime())
                    ));
                    return item;
                })
                .toList();

        List<Map<String, Object>> activeEventPayload = activeEvents.stream()
                .sorted(Comparator.comparing(GovernanceEvent::getLastActiveAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_EXISTING_EVENTS)
                .map(event -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("eventId", event.getId());
                    item.put("title", event.getTitle());
                    item.put("summary", textSupport.abbreviate(event.getSummary(), 140));
                    item.put("sourceCount", event.getSourceCount());
                    item.put("reportCount", event.getReportCount());
                    item.put("riskLevel", event.getRiskLevel());
                    item.put("status", event.getStatus());
                    return item;
                })
                .toList();

        Map<String, Object> aiResult = governanceAiSupportService.completeJson(
                "You are generating final campus governance events from already merged candidate groups. "
                        + "Return JSON only. Use Chinese for title, summary, and reason. "
                        + "Keep only high-quality, governance-relevant events. Never output more than 5 final events.",
                """
                        Return JSON only with this schema:
                        {
                          "events":[
                            {
                              "decision":"CREATE|UPDATE_EXISTING",
                              "groupKeys":["group1","group2"],
                              "targetEventId":123,
                              "title":"Chinese normalized title within 28 chars",
                              "summary":"Chinese summary within 120 chars",
                              "riskHint":"LOW|MEDIUM|HIGH",
                              "qualityScore":90,
                              "reason":"Chinese brief reason"
                            }
                          ],
                          "discardedGroupKeys":["group9"]
                        }

                        Rules:
                        - groupKeys that describe the same final event must be combined into one event record.
                        - Prefer UPDATE_EXISTING when an active event clearly matches the merged candidate.
                        - Titles must be normalized summaries, not copied question sentences.
                        - Keep only the most important events and never output more than 5.

                        Merged candidate groups:
                        %s

                        Existing active events:
                        %s
                        """.formatted(groupPayload, activeEventPayload));

        Object events = aiResult.get("events");
        if (!(events instanceof List<?> rawEvents) || rawEvents.isEmpty()) {
            return List.of();
        }

        Map<String, MergedCandidateGroup> groupMap = groups.stream()
                .collect(Collectors.toMap(group -> group.groupKey, group -> group, (left, right) -> left, LinkedHashMap::new));
        Set<String> discarded = readStringList(aiResult.get("discardedGroupKeys")).stream().collect(Collectors.toSet());
        Set<String> assigned = new LinkedHashSet<>();
        List<EventDecision> decisions = new ArrayList<>();

        for (Object item : rawEvents) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            String decisionType = normalizeDecision(safeText(rawMap.get("decision")));
            Long targetEventId = toLong(rawMap.get("targetEventId"));
            List<String> groupKeys = readStringList(rawMap.get("groupKeys"));
            List<MergedCandidateGroup> selectedGroups = groupKeys.stream()
                    .filter(groupMap::containsKey)
                    .filter(groupKey -> !discarded.contains(groupKey))
                    .filter(groupKey -> assigned.add(groupKey))
                    .map(groupMap::get)
                    .toList();
            if (selectedGroups.isEmpty()) {
                continue;
            }
            decisions.add(new EventDecision(
                    decisionType,
                    targetEventId,
                    selectedGroups,
                    textSupport.abbreviate(safeText(rawMap.get("title")), 60),
                    textSupport.abbreviate(safeText(rawMap.get("summary")), 180),
                    normalizeRisk(safeText(rawMap.get("riskHint"))),
                    intValue(rawMap.get("qualityScore"), 80),
                    textSupport.abbreviate(safeText(rawMap.get("reason")), 80)
            ));
        }

        for (MergedCandidateGroup group : groups) {
            if (!assigned.contains(group.groupKey) && !discarded.contains(group.groupKey)) {
                decisions.add(new EventDecision(
                        group.targetEventId == null ? "CREATE" : "UPDATE_EXISTING",
                        group.targetEventId,
                        List.of(group),
                        group.draftTitle(),
                        group.draftSummary(),
                        group.riskHint(),
                        72,
                        "未被 AI 丢弃，按规则补入"
                ));
            }
        }
        return decisions;
    }

    private List<EventDecision> ruleBasedFinalDecisions(List<MergedCandidateGroup> groups, List<GovernanceEvent> activeEvents) {
        List<EventDecision> decisions = new ArrayList<>();
        for (MergedCandidateGroup group : groups.stream()
                .sorted(Comparator.comparingInt(MergedCandidateGroup::priorityScore).reversed())
                .toList()) {
            if (decisions.size() >= MAX_FINAL_EVENTS) {
                break;
            }
            Long targetEventId = group.targetEventId;
            String decision = targetEventId == null ? "CREATE" : "UPDATE_EXISTING";
            if (targetEventId == null) {
                targetEventId = findBestExistingEvent(group, activeEvents).map(GovernanceEvent::getId).orElse(null);
                if (targetEventId != null && group.ruleScore() >= 4) {
                    decision = "UPDATE_EXISTING";
                }
            }
            decisions.add(new EventDecision(
                    decision,
                    targetEventId,
                    List.of(group),
                    group.draftTitle(),
                    group.draftSummary(),
                    group.riskHint(),
                    68 + Math.min(20, group.priorityScore() * 2),
                    "规则兜底保留"
            ));
        }
        return decisions;
    }

    private List<EventDecision> deduplicateDecisions(List<EventDecision> decisions, List<GovernanceEvent> activeEvents) {
        List<EventDecision> normalized = decisions.stream()
                .sorted(Comparator.comparingInt(EventDecision::priorityScore).reversed())
                .toList();
        List<EventDecision> deduped = new ArrayList<>();
        for (EventDecision decision : normalized) {
            EventDecision matched = null;
            for (EventDecision existing : deduped) {
                if (decisionSimilarity(existing, decision) >= EVENT_MATCH_THRESHOLD) {
                    matched = existing;
                    break;
                }
            }
            if (matched == null) {
                deduped.add(decision);
            } else {
                matched.merge(decision);
            }
        }

        for (EventDecision decision : deduped) {
            if (decision.targetEventId == null) {
                findBestExistingEvent(decision, activeEvents).ifPresent(event -> decision.targetEventId = event.getId());
            }
        }

        return deduped.stream()
                .sorted(Comparator.comparingInt(EventDecision::priorityScore).reversed())
                .limit(MAX_FINAL_EVENTS)
                .toList();
    }

    private GovernanceEvent resolveTargetEvent(EventDecision decision, Map<Long, GovernanceEvent> activeEventMap) {
        if ("UPDATE_EXISTING".equals(decision.decision) && decision.targetEventId != null) {
            GovernanceEvent event = activeEventMap.get(decision.targetEventId);
            if (event != null) {
                return event;
            }
        }
        for (MergedCandidateGroup group : decision.groups) {
            if (group.targetEventId != null) {
                GovernanceEvent event = activeEventMap.get(group.targetEventId);
                if (event != null) {
                    return event;
                }
            }
        }
        return new GovernanceEvent();
    }

    private GovernanceEvent createEvent(GovernanceSignal seedSignal, String title, String summary) {
        Map<String, Object> metadata = textSupport.readJsonObject(seedSignal.getMetadataJson());
        GovernanceEvent event = new GovernanceEvent();
        event.setEventCode("GE-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "-" + UUID.randomUUID().toString().substring(0, 8));
        event.setTitle(resolveDraftTitle(title, metadata, seedSignal));
        event.setSummary(resolveDraftSummary(summary, seedSignal));
        event.setStatus("OPEN");
        event.setStartedAt(seedSignal.getSignalTime());
        event.setLastActiveAt(seedSignal.getSignalTime());
        return governanceEventRepository.save(event);
    }

    private void refreshEventDraft(GovernanceEvent event, EventDecision decision) {
        GovernanceSignal firstSignal = decision.primarySignal();
        Map<String, Object> metadata = textSupport.readJsonObject(firstSignal.getMetadataJson());
        if (event.getTitle() == null || event.getTitle().isBlank() || decision.qualityScore >= 75) {
            event.setTitle(resolveDraftTitle(decision.title, metadata, firstSignal));
        }
        if (decision.summary != null && !decision.summary.isBlank()) {
            event.setSummary(textSupport.abbreviate(decision.summary, 180));
        } else if (event.getSummary() == null || event.getSummary().isBlank()) {
            event.setSummary(resolveDraftSummary("", firstSignal));
        }
        event.setStartedAt(minSignalTime(event.getStartedAt(), decision.signals()));
        event.setLastActiveAt(maxSignalTime(event.getLastActiveAt(), decision.signals()));
        event.setClusterVersion(Math.max(1, event.getClusterVersion() == null ? 1 : event.getClusterVersion() + 1));
    }

    private void attachSignal(GovernanceSignal signal, GovernanceEvent event) {
        signal.setEvent(event);
        signal.setIngestStatus("CLUSTERED");
        governanceSignalRepository.save(signal);

        addEventSource(event, signal.getSignalType(), signal.getSourceId(), 1);
        Map<String, Object> metadata = textSupport.readJsonObject(signal.getMetadataJson());
        Long postId = toLong(metadata.get("postId"));
        if (postId != null) {
            addEventSource(event, "POST", postId, 2);
        }
    }

    private void recalculateEventStats(GovernanceEvent event) {
        event.setSourceCount((int) governanceSignalRepository.countByEventId(event.getId()));
        event.setReportCount((int) governanceSignalRepository.countByEventIdAndSignalType(event.getId(), "REPORT"));
        governanceEventRepository.save(event);
    }

    private void addEventSource(GovernanceEvent event, String sourceType, Long sourceId, int weight) {
        if (sourceId == null || governanceEventSourceRepository.existsByEventIdAndSourceTypeAndSourceId(
                event.getId(), sourceType, sourceId)) {
            return;
        }
        GovernanceEventSource source = new GovernanceEventSource();
        source.setEvent(event);
        source.setSourceType(sourceType);
        source.setSourceId(sourceId);
        source.setWeight(weight);
        governanceEventSourceRepository.save(source);
    }

    private void markDiscardedSignals(List<GovernanceSignal> signals) {
        for (GovernanceSignal signal : signals) {
            signal.setIngestStatus("IGNORED");
        }
        governanceSignalRepository.saveAll(signals);
    }

    private Optional<GovernanceEvent> findBestExistingEvent(MergedCandidateGroup group, List<GovernanceEvent> activeEvents) {
        double bestScore = 0.0;
        GovernanceEvent bestEvent = null;
        for (GovernanceEvent event : activeEvents) {
            double score = eventSimilarity(group.keywords(), group.canonicalHint(), event);
            if (score > bestScore) {
                bestScore = score;
                bestEvent = event;
            }
        }
        return bestScore >= EVENT_MATCH_THRESHOLD ? Optional.ofNullable(bestEvent) : Optional.empty();
    }

    private Optional<GovernanceEvent> findBestExistingEvent(EventDecision decision, List<GovernanceEvent> activeEvents) {
        double bestScore = 0.0;
        GovernanceEvent bestEvent = null;
        for (GovernanceEvent event : activeEvents) {
            double score = eventSimilarity(decision.keywords(), decision.canonicalHint(), event);
            if (score > bestScore) {
                bestScore = score;
                bestEvent = event;
            }
        }
        return bestScore >= EVENT_MATCH_THRESHOLD ? Optional.ofNullable(bestEvent) : Optional.empty();
    }

    private double eventSimilarity(List<String> keywords, String canonicalHint, GovernanceEvent event) {
        List<String> eventKeywords = textSupport.extractKeywords(List.of(event.getTitle(), event.getSummary()), 10);
        double score = overlapScore(keywords, eventKeywords);
        String eventHint = normalizeSemanticKey(event.getTitle() + " " + event.getSummary());
        if (!canonicalHint.isBlank() && !eventHint.isBlank() && Objects.equals(canonicalHint, eventHint)) {
            score += 0.24;
        }
        return Math.min(1.0, score);
    }

    private double similarity(GovernanceSignal signal, CandidateCluster cluster) {
        Map<String, Object> metadata = textSupport.readJsonObject(signal.getMetadataJson());
        Long signalPostId = toLong(metadata.get("postId"));
        if (cluster.postId != null && Objects.equals(cluster.postId, signalPostId)) {
            return 1.0;
        }

        String comparableTitle = comparableTitle(metadata);
        if (!comparableTitle.isBlank() && !cluster.comparableTitle.isBlank()
                && (cluster.comparableTitle.contains(comparableTitle) || comparableTitle.contains(cluster.comparableTitle))) {
            return 0.88;
        }

        List<String> signalKeywords = signalKeywords(signal, metadata);
        double keywordScore = overlapScore(signalKeywords, cluster.keywords);
        boolean sameHint = signalKeywords.stream().anyMatch(cluster.governanceHints::contains);
        boolean nearTime = cluster.lastSignalTime != null && signal.getSignalTime() != null
                && Math.abs(Duration.between(cluster.lastSignalTime, signal.getSignalTime()).toHours()) <= 36;
        double score = keywordScore;
        if (sameHint) {
            score += 0.18;
        }
        if (nearTime) {
            score += 0.08;
        }
        return Math.min(1.0, score);
    }

    private double groupSimilarity(MergedCandidateGroup group, CandidateCluster cluster) {
        double score = overlapScore(group.keywords(), cluster.keywords);
        if (Objects.equals(group.canonicalHint(), cluster.canonicalHint())) {
            score += 0.22;
        }
        if (hasCloseTime(group.firstSignalTime(), group.lastSignalTime(), cluster.firstSignalTime, cluster.lastSignalTime)) {
            score += 0.08;
        }
        return Math.min(1.0, score);
    }

    private double groupToGroupSimilarity(MergedCandidateGroup left, MergedCandidateGroup right) {
        double score = overlapScore(left.keywords(), right.keywords());
        if (Objects.equals(left.canonicalHint(), right.canonicalHint())) {
            score += 0.22;
        }
        if (hasCloseTime(left.firstSignalTime(), left.lastSignalTime(), right.firstSignalTime(), right.lastSignalTime())) {
            score += 0.08;
        }
        return Math.min(1.0, score);
    }

    private double decisionSimilarity(EventDecision left, EventDecision right) {
        double score = overlapScore(left.keywords(), right.keywords());
        if (Objects.equals(left.canonicalHint(), right.canonicalHint())) {
            score += 0.24;
        }
        if (hasCloseTime(left.firstSignalTime(), left.lastSignalTime(), right.firstSignalTime(), right.lastSignalTime())) {
            score += 0.08;
        }
        return Math.min(1.0, score);
    }

    private boolean hasCloseTime(LocalDateTime startA, LocalDateTime endA, LocalDateTime startB, LocalDateTime endB) {
        if (endA == null || endB == null) {
            return false;
        }
        return Math.abs(Duration.between(endA, endB).toHours()) <= 48
                || (startA != null && startB != null && Math.abs(Duration.between(startA, startB).toHours()) <= 48);
    }

    private double overlapScore(Collection<String> left, Collection<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> normalizedRight = right.stream().map(this::normalizeToken).collect(Collectors.toSet());
        long overlap = left.stream()
                .map(this::normalizeToken)
                .filter(normalizedRight::contains)
                .count();
        return overlap * 1.0 / Math.max(left.size(), right.size());
    }

    private List<String> signalKeywords(GovernanceSignal signal, Map<String, Object> metadata) {
        List<String> texts = new ArrayList<>();
        texts.add(signal.getContentText());
        texts.add(String.valueOf(metadata.getOrDefault("title", "")));
        texts.add(String.valueOf(metadata.getOrDefault("relatedTitle", "")));
        texts.add(String.valueOf(metadata.getOrDefault("postTitle", "")));
        texts.add(String.valueOf(metadata.getOrDefault("reason", "")));
        Object tags = metadata.get("tags");
        if (tags instanceof List<?> list) {
            list.forEach(item -> texts.add(String.valueOf(item)));
        }
        return textSupport.extractKeywords(texts, 10);
    }

    private List<String> extractGovernanceHints(String content, Map<String, Object> metadata) {
        List<String> hints = new ArrayList<>();
        List<String> texts = List.of(
                textSupport.normalizeText(content),
                textSupport.normalizeText(String.valueOf(metadata.getOrDefault("title", ""))),
                textSupport.normalizeText(String.valueOf(metadata.getOrDefault("relatedTitle", ""))),
                textSupport.normalizeText(String.valueOf(metadata.getOrDefault("postTitle", ""))),
                textSupport.normalizeText(String.valueOf(metadata.getOrDefault("reason", "")))
        );
        for (String hint : GOVERNANCE_HINTS) {
            if (texts.stream().anyMatch(text -> text.contains(hint))) {
                hints.add(hint);
            }
        }
        return hints;
    }

    private String resolveDraftTitle(String preferredTitle, Map<String, Object> metadata, GovernanceSignal signal) {
        String title = textSupport.normalizeText(preferredTitle);
        if (!title.isBlank()) {
            return textSupport.abbreviate(title, 60);
        }
        String metadataTitle = textSupport.normalizeText(String.valueOf(metadata.getOrDefault("title", "")));
        if (!metadataTitle.isBlank()) {
            return textSupport.abbreviate(metadataTitle, 60);
        }
        String relatedTitle = textSupport.normalizeText(String.valueOf(metadata.getOrDefault("relatedTitle", "")));
        if (!relatedTitle.isBlank()) {
            return "围绕“" + textSupport.abbreviate(relatedTitle, 26) + "”的讨论";
        }
        String postTitle = textSupport.normalizeText(String.valueOf(metadata.getOrDefault("postTitle", "")));
        if (!postTitle.isBlank()) {
            return "围绕“" + textSupport.abbreviate(postTitle, 26) + "”的讨论";
        }
        return textSupport.abbreviate(signal.getContentText(), 48);
    }

    private String resolveDraftSummary(String summary, GovernanceSignal signal) {
        String normalized = textSupport.normalizeText(summary);
        if (!normalized.isBlank()) {
            return textSupport.abbreviate(normalized, 180);
        }
        return textSupport.abbreviate(signal.getContentText(), 160);
    }

    private String comparableTitle(Map<String, Object> metadata) {
        String title = textSupport.normalizeText(String.valueOf(metadata.getOrDefault("title", "")));
        if (!title.isBlank()) {
            return title.toLowerCase(Locale.ROOT);
        }
        String relatedTitle = textSupport.normalizeText(String.valueOf(metadata.getOrDefault("relatedTitle", "")));
        if (!relatedTitle.isBlank()) {
            return relatedTitle.toLowerCase(Locale.ROOT);
        }
        String postTitle = textSupport.normalizeText(String.valueOf(metadata.getOrDefault("postTitle", "")));
        return postTitle.toLowerCase(Locale.ROOT);
    }

    private Long resolveLinkedEventId(GovernanceSignal signal, Map<String, Object> metadata, List<GovernanceEvent> activeEvents) {
        Optional<GovernanceEventSource> exactSource = governanceEventSourceRepository
                .findFirstBySourceTypeAndSourceIdOrderByCreatedAtDesc(signal.getSignalType(), signal.getSourceId());
        if (exactSource.isPresent()) {
            return exactSource.get().getEvent().getId();
        }

        Long postId = toLong(metadata.get("postId"));
        if (postId != null) {
            Optional<GovernanceEventSource> postLink = governanceEventSourceRepository
                    .findFirstBySourceTypeAndSourceIdOrderByCreatedAtDesc("POST", postId);
            if (postLink.isPresent()) {
                return postLink.get().getEvent().getId();
            }
        }

        String title = comparableTitle(metadata);
        if (!title.isBlank()) {
            for (GovernanceEvent event : activeEvents) {
                String candidateTitle = textSupport.normalizeText(event.getTitle()).toLowerCase(Locale.ROOT);
                if (!candidateTitle.isBlank() && (candidateTitle.contains(title) || title.contains(candidateTitle))) {
                    return event.getId();
                }
            }
        }
        return null;
    }

    private String normalizeDecision(String value) {
        return "UPDATE_EXISTING".equals(textSupport.normalizeText(value).toUpperCase(Locale.ROOT))
                ? "UPDATE_EXISTING" : "CREATE";
    }

    private String normalizeRisk(String value) {
        String normalized = textSupport.normalizeText(value).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIGH", "MEDIUM", "LOW" -> normalized;
            default -> "LOW";
        };
    }

    private String normalizeToken(String value) {
        return textSupport.normalizeText(value).toLowerCase(Locale.ROOT);
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizeGroupKey(String value, List<CandidateCluster> clusters) {
        String candidate = textSupport.normalizeText(value);
        if (!candidate.isBlank()) {
            return candidate;
        }
        return normalizeSemanticKey(clusters.stream()
                .map(CandidateCluster::canonicalHint)
                .collect(Collectors.joining(" ")));
    }

    private String normalizeSemanticKey(String text) {
        String normalized = textSupport.normalizeText(text).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "group_" + UUID.randomUUID().toString().substring(0, 8);
        }
        List<String> keywords = textSupport.extractKeywords(List.of(normalized), 4);
        if (keywords.isEmpty()) {
            return "group_" + UUID.randomUUID().toString().substring(0, 8);
        }
        return keywords.stream().map(this::normalizeToken).collect(Collectors.joining("_"));
    }

    private List<String> readStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .map(textSupport::normalizeText)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private LocalDateTime minSignalTime(LocalDateTime current, List<GovernanceSignal> signals) {
        LocalDateTime min = current;
        for (GovernanceSignal signal : signals) {
            if (signal.getSignalTime() == null) {
                continue;
            }
            if (min == null || signal.getSignalTime().isBefore(min)) {
                min = signal.getSignalTime();
            }
        }
        return min;
    }

    private LocalDateTime maxSignalTime(LocalDateTime current, List<GovernanceSignal> signals) {
        LocalDateTime max = current;
        for (GovernanceSignal signal : signals) {
            if (signal.getSignalTime() == null) {
                continue;
            }
            if (max == null || signal.getSignalTime().isAfter(max)) {
                max = signal.getSignalTime();
            }
        }
        return max;
    }

    private final class CandidateCluster {
        private final String id;
        private final List<GovernanceSignal> signals = new ArrayList<>();
        private final List<String> keywords = new ArrayList<>();
        private final List<String> governanceHints = new ArrayList<>();
        private final List<String> representativeTexts = new ArrayList<>();
        private final String comparableTitle;
        private final Long postId;
        private Long linkedEventId;
        private int reportCount;
        private int signalCount;
        private int ruleScore;
        private String draftTitle = "";
        private String draftSummary = "";
        private String riskHint = "LOW";
        private LocalDateTime firstSignalTime;
        private LocalDateTime lastSignalTime;

        private CandidateCluster(String id, GovernanceSignal seedSignal, List<GovernanceEvent> activeEvents) {
            this.id = id;
            Map<String, Object> metadata = textSupport.readJsonObject(seedSignal.getMetadataJson());
            this.comparableTitle = comparableTitle(metadata);
            this.postId = toLong(metadata.get("postId"));
            this.linkedEventId = resolveLinkedEventId(seedSignal, metadata, activeEvents);
            addSignal(seedSignal);
        }

        private void addSignal(GovernanceSignal signal) {
            signals.add(signal);
            signalCount++;
            if ("REPORT".equals(signal.getSignalType())) {
                reportCount++;
            }
            firstSignalTime = firstSignalTime == null || signal.getSignalTime().isBefore(firstSignalTime)
                    ? signal.getSignalTime() : firstSignalTime;
            lastSignalTime = lastSignalTime == null || signal.getSignalTime().isAfter(lastSignalTime)
                    ? signal.getSignalTime() : lastSignalTime;

            Map<String, Object> metadata = textSupport.readJsonObject(signal.getMetadataJson());
            keywords.addAll(signalKeywords(signal, metadata));
            governanceHints.addAll(extractGovernanceHints(signal.getContentText(), metadata));
            representativeTexts.add(textSupport.abbreviate(signal.getContentText(), 80));
        }

        private void finalizeDraft() {
            keywords.clear();
            keywords.addAll(textSupport.deduplicatePreserveOrder(keywords, 10));
            governanceHints.clear();
            governanceHints.addAll(textSupport.deduplicatePreserveOrder(governanceHints, 8));
            representativeTexts.clear();
            representativeTexts.addAll(textSupport.deduplicatePreserveOrder(representativeTexts, 4));

            GovernanceSignal firstSignal = signals.get(0);
            Map<String, Object> metadata = textSupport.readJsonObject(firstSignal.getMetadataJson());
            draftTitle = resolveDraftTitle(buildTitleFromHints(metadata, firstSignal), metadata, firstSignal);
            draftSummary = buildDraftSummary();
            ruleScore = calculateRuleScore();
            riskHint = reportCount >= 2 || ruleScore >= 6 ? "HIGH" : ruleScore >= 4 ? "MEDIUM" : "LOW";
        }

        private String buildTitleFromHints(Map<String, Object> metadata, GovernanceSignal signal) {
            String metadataTitle = textSupport.normalizeText(String.valueOf(metadata.getOrDefault("title", "")));
            if (!metadataTitle.isBlank() && metadataTitle.length() >= 6) {
                return metadataTitle;
            }
            String relatedTitle = textSupport.normalizeText(String.valueOf(metadata.getOrDefault("relatedTitle", "")));
            if (!relatedTitle.isBlank() && relatedTitle.length() >= 6) {
                return relatedTitle;
            }
            String postTitle = textSupport.normalizeText(String.valueOf(metadata.getOrDefault("postTitle", "")));
            if (!postTitle.isBlank() && postTitle.length() >= 6) {
                return postTitle;
            }
            if (!governanceHints.isEmpty()) {
                return String.join(" ", governanceHints);
            }
            return textSupport.abbreviate(signal.getContentText(), 40);
        }

        private String buildDraftSummary() {
            if (!representativeTexts.isEmpty() && !governanceHints.isEmpty()) {
                return "围绕" + String.join("、", governanceHints.stream().limit(3).toList())
                        + "出现集中讨论，共涉及 " + signalCount + " 条信号"
                        + (reportCount > 0 ? "，其中举报 " + reportCount + " 条" : "")
                        + "。代表性表述包括：" + String.join("；", representativeTexts.stream().limit(2).toList());
            }
            if (!representativeTexts.isEmpty()) {
                return "本轮监测共聚合 " + signalCount + " 条相关信号，代表性内容包括：" + String.join("；", representativeTexts.stream().limit(2).toList());
            }
            return "本轮监测聚合到 " + signalCount + " 条可能相关的治理信号。";
        }

        private int calculateRuleScore() {
            int score = 0;
            if (signalCount >= 2) {
                score += 2;
            }
            if (signalCount >= 4) {
                score += 1;
            }
            if (reportCount > 0) {
                score += 2;
            }
            if (!governanceHints.isEmpty()) {
                score += Math.min(3, governanceHints.size());
            }
            if (representativeTexts.stream().anyMatch(this::isLikelyNonGovernance)) {
                score -= 4;
            }
            if (!representativeTexts.isEmpty() && representativeTexts.stream().allMatch(this::isLowInformationText)) {
                score -= 4;
            }
            return score;
        }

        private boolean isLikelyNonGovernance(String text) {
            String normalized = textSupport.normalizeText(text);
            return NON_GOVERNANCE_MARKERS.stream().anyMatch(normalized::contains);
        }

        private boolean isLowInformationText(String text) {
            String normalized = textSupport.normalizeText(text);
            if (normalized.length() <= 6) {
                return true;
            }
            return LOW_INFORMATION_TEXTS.stream().anyMatch(normalized::contains);
        }

        private int priorityScore() {
            return ruleScore + Math.min(4, signalCount) + Math.min(3, reportCount * 2);
        }

        private String canonicalHint() {
            if (!governanceHints.isEmpty()) {
                return normalizeSemanticKey(String.join(" ", governanceHints));
            }
            if (!keywords.isEmpty()) {
                return normalizeSemanticKey(String.join(" ", keywords));
            }
            return normalizeSemanticKey(draftTitle + " " + draftSummary);
        }
    }

    private final class MergedCandidateGroup {
        private final String groupKey;
        private final List<CandidateCluster> clusters = new ArrayList<>();
        private Long targetEventId;

        private MergedCandidateGroup(String groupKey, List<CandidateCluster> clusters) {
            this.groupKey = groupKey;
            this.clusters.addAll(clusters);
        }

        private void addCluster(CandidateCluster cluster) {
            if (clusters.stream().noneMatch(item -> Objects.equals(item.id, cluster.id))) {
                clusters.add(cluster);
            }
        }

        private void merge(MergedCandidateGroup other) {
            other.clusters.forEach(this::addCluster);
            if (targetEventId == null) {
                targetEventId = other.targetEventId;
            }
        }

        private int signalCount() {
            return clusters.stream().mapToInt(cluster -> cluster.signalCount).sum();
        }

        private int reportCount() {
            return clusters.stream().mapToInt(cluster -> cluster.reportCount).sum();
        }

        private int ruleScore() {
            return clusters.stream().mapToInt(cluster -> cluster.ruleScore).max().orElse(0);
        }

        private int priorityScore() {
            return ruleScore() + Math.min(5, signalCount()) + Math.min(4, reportCount() * 2);
        }

        private List<String> keywords() {
            List<String> merged = new ArrayList<>();
            clusters.forEach(cluster -> merged.addAll(cluster.keywords));
            return textSupport.deduplicatePreserveOrder(merged, 10);
        }

        private List<String> governanceHints() {
            List<String> merged = new ArrayList<>();
            clusters.forEach(cluster -> merged.addAll(cluster.governanceHints));
            return textSupport.deduplicatePreserveOrder(merged, 8);
        }

        private List<String> representativeTexts() {
            List<String> merged = new ArrayList<>();
            clusters.forEach(cluster -> merged.addAll(cluster.representativeTexts));
            return textSupport.deduplicatePreserveOrder(merged, 5);
        }

        private String draftTitle() {
            List<String> hints = governanceHints();
            if (!hints.isEmpty()) {
                if (hints.contains("封校") || hints.contains("返校")) {
                    return "周末封校传言引发集中讨论";
                }
                return textSupport.abbreviate(String.join("、", hints.stream().limit(3).toList()) + "相关讨论", 60);
            }
            return textSupport.abbreviate(clusters.get(0).draftTitle, 60);
        }

        private String draftSummary() {
            List<String> texts = representativeTexts();
            List<String> hints = governanceHints();
            if (!texts.isEmpty() && !hints.isEmpty()) {
                return "围绕" + String.join("、", hints.stream().limit(3).toList())
                        + "的讨论被归并为同一治理事件，累计 " + signalCount() + " 条信号"
                        + (reportCount() > 0 ? "，其中举报 " + reportCount() + " 条" : "")
                        + "。代表性表述包括：" + String.join("；", texts.stream().limit(2).toList());
            }
            return textSupport.abbreviate(clusters.get(0).draftSummary, 180);
        }

        private String riskHint() {
            if (reportCount() >= 2 || ruleScore() >= 6) {
                return "HIGH";
            }
            if (ruleScore() >= 4 || signalCount() >= 4) {
                return "MEDIUM";
            }
            return "LOW";
        }

        private String canonicalHint() {
            List<String> hints = governanceHints();
            if (!hints.isEmpty()) {
                return normalizeSemanticKey(String.join(" ", hints));
            }
            return normalizeSemanticKey(String.join(" ", keywords()));
        }

        private LocalDateTime firstSignalTime() {
            return clusters.stream()
                    .map(cluster -> cluster.firstSignalTime)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
        }

        private LocalDateTime lastSignalTime() {
            return clusters.stream()
                    .map(cluster -> cluster.lastSignalTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
        }
    }

    private static final class MergePhaseResult {
        private final List<MergedCandidateGroup> groups;
        private final boolean aiUsed;
        private final String mode;

        private MergePhaseResult(List<MergedCandidateGroup> groups, boolean aiUsed, String mode) {
            this.groups = groups;
            this.aiUsed = aiUsed;
            this.mode = mode;
        }
    }

    private final class EventDecision {
        private String decision;
        private Long targetEventId;
        private final List<MergedCandidateGroup> groups = new ArrayList<>();
        private String title;
        private String summary;
        private String riskHint;
        private int qualityScore;
        private String reason;

        private EventDecision(String decision,
                              Long targetEventId,
                              List<MergedCandidateGroup> groups,
                              String title,
                              String summary,
                              String riskHint,
                              int qualityScore,
                              String reason) {
            this.decision = decision;
            this.targetEventId = targetEventId;
            this.groups.addAll(groups);
            this.title = title;
            this.summary = summary;
            this.riskHint = riskHint;
            this.qualityScore = qualityScore;
            this.reason = reason;
        }

        private void merge(EventDecision other) {
            other.groups.forEach(group -> {
                if (groups.stream().noneMatch(item -> Objects.equals(item.groupKey, group.groupKey))) {
                    groups.add(group);
                }
            });
            if (targetEventId == null) {
                targetEventId = other.targetEventId;
            }
            if (qualityScore < other.qualityScore) {
                qualityScore = other.qualityScore;
                title = other.title;
                summary = other.summary;
                riskHint = other.riskHint;
                reason = other.reason;
            }
            if ("UPDATE_EXISTING".equals(other.decision)) {
                decision = "UPDATE_EXISTING";
            }
        }

        private List<GovernanceSignal> signals() {
            return groups.stream()
                    .flatMap(group -> group.clusters.stream())
                    .flatMap(cluster -> cluster.signals.stream())
                    .distinct()
                    .sorted(Comparator.comparing(GovernanceSignal::getSignalTime))
                    .toList();
        }

        private GovernanceSignal primarySignal() {
            return signals().get(0);
        }

        private List<String> keywords() {
            List<String> merged = new ArrayList<>();
            groups.forEach(group -> merged.addAll(group.keywords()));
            return textSupport.deduplicatePreserveOrder(merged, 10);
        }

        private String canonicalHint() {
            return normalizeSemanticKey(title + " " + summary + " " + String.join(" ", keywords()));
        }

        private int priorityScore() {
            int signalCount = groups.stream().mapToInt(MergedCandidateGroup::signalCount).sum();
            int reportCount = groups.stream().mapToInt(MergedCandidateGroup::reportCount).sum();
            return qualityScore + Math.min(12, signalCount) + Math.min(10, reportCount * 3);
        }

        private LocalDateTime firstSignalTime() {
            return signals().stream().map(GovernanceSignal::getSignalTime).min(LocalDateTime::compareTo).orElse(null);
        }

        private LocalDateTime lastSignalTime() {
            return signals().stream().map(GovernanceSignal::getSignalTime).max(LocalDateTime::compareTo).orElse(null);
        }
    }

    private static final class FinalizePhaseResult {
        private final List<EventDecision> decisions;
        private final int selectedClusterCount;
        private final boolean aiUsed;
        private final String mode;

        private FinalizePhaseResult(List<EventDecision> decisions, int selectedClusterCount, boolean aiUsed, String mode) {
            this.decisions = decisions;
            this.selectedClusterCount = selectedClusterCount;
            this.aiUsed = aiUsed;
            this.mode = mode;
        }
    }
}
