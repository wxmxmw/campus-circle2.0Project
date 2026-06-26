package com.campus.campus_backend.service.governance;

import com.campus.campus_backend.common.BizException;
import com.campus.campus_backend.common.ErrorCode;
import com.campus.campus_backend.domain.governance.GovernanceAction;
import com.campus.campus_backend.domain.governance.GovernanceEvent;
import com.campus.campus_backend.domain.governance.GovernanceEvidenceRef;
import com.campus.campus_backend.domain.governance.GovernanceSignal;
import com.campus.campus_backend.domain.governance.GovernanceSnapshot;
import com.campus.campus_backend.repository.governance.GovernanceActionRepository;
import com.campus.campus_backend.repository.governance.GovernanceEventRepository;
import com.campus.campus_backend.repository.governance.GovernanceEvidenceRefRepository;
import com.campus.campus_backend.repository.governance.GovernanceSignalRepository;
import com.campus.campus_backend.repository.governance.GovernanceSnapshotRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GovernanceViewService {
    private final GovernanceEventRepository governanceEventRepository;
    private final GovernanceSignalRepository governanceSignalRepository;
    private final GovernanceSnapshotRepository governanceSnapshotRepository;
    private final GovernanceActionRepository governanceActionRepository;
    private final GovernanceEvidenceRefRepository governanceEvidenceRefRepository;
    private final GovernanceTextSupport textSupport;

    public GovernanceViewService(GovernanceEventRepository governanceEventRepository,
                                 GovernanceSignalRepository governanceSignalRepository,
                                 GovernanceSnapshotRepository governanceSnapshotRepository,
                                 GovernanceActionRepository governanceActionRepository,
                                 GovernanceEvidenceRefRepository governanceEvidenceRefRepository,
                                 GovernanceTextSupport textSupport) {
        this.governanceEventRepository = governanceEventRepository;
        this.governanceSignalRepository = governanceSignalRepository;
        this.governanceSnapshotRepository = governanceSnapshotRepository;
        this.governanceActionRepository = governanceActionRepository;
        this.governanceEvidenceRefRepository = governanceEvidenceRefRepository;
        this.textSupport = textSupport;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listEvents(String status, String riskLevel, String rumorStatus, int page, int pageSize) {
        Page<GovernanceEvent> events = governanceEventRepository.search(
                blankToNull(status), blankToNull(riskLevel), blankToNull(rumorStatus),
                PageRequest.of(Math.max(page - 1, 0), Math.max(1, pageSize)));
        List<Map<String, Object>> list = events.getContent().stream().map(this::toListItem).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("total", events.getTotalElements());
        result.put("hasMore", events.hasNext());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEventDetail(Long eventId) {
        GovernanceEvent event = governanceEventRepository.findById(eventId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        GovernanceSnapshot latestSnapshot = governanceSnapshotRepository.findTopByEventIdOrderByVersionNoDesc(eventId).orElse(null);
        List<GovernanceSignal> signals = governanceSignalRepository.findByEventIdOrderBySignalTimeAsc(eventId);
        List<GovernanceSnapshot> snapshots = governanceSnapshotRepository.findByEventIdOrderByVersionNoDesc(eventId);
        List<GovernanceAction> actions = governanceActionRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        List<GovernanceEvidenceRef> evidence = governanceEvidenceRefRepository.findByEventIdOrderByCreatedAtDesc(eventId);

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("id", event.getId());
        overview.put("eventCode", event.getEventCode());
        overview.put("title", event.getTitle());
        overview.put("summary", event.getSummary());
        overview.put("status", event.getStatus());
        overview.put("riskLevel", event.getRiskLevel());
        overview.put("riskScore", event.getRiskScore());
        overview.put("rumorStatus", event.getRumorStatus());
        overview.put("sourceCount", event.getSourceCount());
        overview.put("reportCount", event.getReportCount());
        overview.put("negativeRatio", event.getNegativeRatio());
        overview.put("startedAt", String.valueOf(event.getStartedAt()));
        overview.put("lastActiveAt", String.valueOf(event.getLastActiveAt()));
        overview.put("resolvedAt", String.valueOf(event.getResolvedAt()));
        result.put("overview", overview);
        result.put("latestSnapshot", latestSnapshot == null ? Map.of() : toSnapshotMap(latestSnapshot));
        result.put("snapshots", snapshots.stream().map(this::toSnapshotMap).toList());
        result.put("signals", signals.stream().map(this::toSignalMap).toList());
        result.put("actions", actions.stream().map(this::toActionMap).toList());
        result.put("evidence", evidence.stream().map(this::toEvidenceMap).toList());
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSnapshots(Long eventId) {
        governanceEventRepository.findById(eventId).orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        return governanceSnapshotRepository.findByEventIdOrderByVersionNoDesc(eventId).stream()
                .map(this::toSnapshotMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listOfficialEvidence(Long eventId, String keyword, int page, int pageSize) {
        Page<GovernanceEvidenceRef> evidencePage = governanceEvidenceRefRepository.searchOfficialEvidence(
                eventId,
                blankToNull(keyword),
                PageRequest.of(Math.max(page - 1, 0), Math.max(1, pageSize)));

        List<Map<String, Object>> list = evidencePage.getContent().stream()
                .map(this::toOfficialEvidenceMap)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("total", evidencePage.getTotalElements());
        result.put("hasMore", evidencePage.hasNext());
        result.put("eventId", eventId);
        result.put("keyword", blankToNull(keyword));
        return result;
    }

    private Map<String, Object> toListItem(GovernanceEvent event) {
        GovernanceSnapshot latestSnapshot = governanceSnapshotRepository.findTopByEventIdOrderByVersionNoDesc(event.getId()).orElse(null);
        Map<String, Object> suggestion = latestSnapshot == null
                ? Map.of()
                : textSupport.readJsonObject(latestSnapshot.getSuggestionResultJson());
        List<Map<String, Object>> actions = List.of();
        if (suggestion.get("recommendedActions") instanceof List<?> list) {
            actions = list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> {
                        Map<?, ?> raw = (Map<?, ?>) item;
                        Map<String, Object> mapped = new LinkedHashMap<>();
                        raw.forEach((key, value) -> mapped.put(String.valueOf(key), value));
                        return mapped;
                    })
                    .toList();
        }
        String currentSuggestion = actions.isEmpty() ? "OBSERVE" : String.valueOf(actions.get(0).get("type"));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", event.getId());
        item.put("eventCode", event.getEventCode());
        item.put("title", event.getTitle());
        item.put("summary", event.getSummary());
        item.put("status", event.getStatus());
        item.put("riskLevel", event.getRiskLevel());
        item.put("riskScore", event.getRiskScore());
        item.put("rumorStatus", event.getRumorStatus());
        item.put("sourceCount", event.getSourceCount());
        item.put("reportCount", event.getReportCount());
        item.put("negativeRatio", event.getNegativeRatio());
        item.put("startedAt", String.valueOf(event.getStartedAt()));
        item.put("lastActiveAt", String.valueOf(event.getLastActiveAt()));
        item.put("currentSuggestion", currentSuggestion);
        return item;
    }

    private Map<String, Object> toSnapshotMap(GovernanceSnapshot snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", snapshot.getId());
        map.put("versionNo", snapshot.getVersionNo());
        map.put("opinionSummary", snapshot.getOpinionSummary());
        map.put("demandsSummary", snapshot.getDemandsSummary());
        map.put("situation", textSupport.readJsonObject(snapshot.getSituationResultJson()));
        map.put("rumor", textSupport.readJsonObject(snapshot.getRumorResultJson()));
        map.put("suggestion", textSupport.readJsonObject(snapshot.getSuggestionResultJson()));
        map.put("trace", textSupport.readJsonObject(snapshot.getModelTraceJson()));
        map.put("createdAt", String.valueOf(snapshot.getCreatedAt()));
        return map;
    }

    private Map<String, Object> toSignalMap(GovernanceSignal signal) {
        Map<String, Object> metadata = textSupport.readJsonObject(signal.getMetadataJson());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", signal.getId());
        map.put("signalType", signal.getSignalType());
        map.put("sourceId", signal.getSourceId());
        map.put("sourceUserId", signal.getSourceUserId());
        map.put("signalTime", String.valueOf(signal.getSignalTime()));
        map.put("contentText", signal.getContentText());
        map.put("metadata", metadata);
        return map;
    }

    private Map<String, Object> toActionMap(GovernanceAction action) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", action.getId());
        map.put("actionType", action.getActionType());
        map.put("actionNote", action.getActionNote());
        map.put("operatorUserId", action.getOperatorUserId());
        map.put("fromAgent", action.getFromAgent());
        map.put("createdAt", String.valueOf(action.getCreatedAt()));
        return map;
    }

    private Map<String, Object> toEvidenceMap(GovernanceEvidenceRef evidence) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", evidence.getId());
        map.put("evidenceType", evidence.getEvidenceType());
        map.put("refId", evidence.getRefId());
        map.put("title", evidence.getTitle());
        map.put("excerpt", evidence.getExcerpt());
        map.put("confidence", evidence.getConfidence());
        map.put("createdAt", String.valueOf(evidence.getCreatedAt()));
        return map;
    }

    private Map<String, Object> toOfficialEvidenceMap(GovernanceEvidenceRef evidence) {
        Map<String, Object> map = toEvidenceMap(evidence);
        GovernanceEvent event = evidence.getEvent();
        Map<String, Object> eventInfo = new LinkedHashMap<>();
        eventInfo.put("id", event.getId());
        eventInfo.put("eventCode", event.getEventCode());
        eventInfo.put("title", event.getTitle());
        eventInfo.put("status", event.getStatus());
        eventInfo.put("riskLevel", event.getRiskLevel());
        map.put("event", eventInfo);
        return map;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
