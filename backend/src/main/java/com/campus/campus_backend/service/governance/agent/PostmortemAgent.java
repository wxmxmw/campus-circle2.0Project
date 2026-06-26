package com.campus.campus_backend.service.governance.agent;

import com.campus.campus_backend.domain.governance.GovernanceAction;
import com.campus.campus_backend.domain.governance.GovernanceEvent;
import com.campus.campus_backend.domain.governance.GovernanceSignal;
import com.campus.campus_backend.domain.governance.GovernanceSnapshot;
import com.campus.campus_backend.repository.governance.GovernanceActionRepository;
import com.campus.campus_backend.repository.governance.GovernanceSignalRepository;
import com.campus.campus_backend.repository.governance.GovernanceSnapshotRepository;
import com.campus.campus_backend.service.governance.GovernanceAiSupportService;
import com.campus.campus_backend.service.governance.GovernanceTextSupport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PostmortemAgent implements GovernanceAgent<GovernanceEvent, Map<String, Object>> {
    private final GovernanceSignalRepository governanceSignalRepository;
    private final GovernanceSnapshotRepository governanceSnapshotRepository;
    private final GovernanceActionRepository governanceActionRepository;
    private final GovernanceTextSupport textSupport;
    private final GovernanceAiSupportService governanceAiSupportService;

    public PostmortemAgent(GovernanceSignalRepository governanceSignalRepository,
                           GovernanceSnapshotRepository governanceSnapshotRepository,
                           GovernanceActionRepository governanceActionRepository,
                           GovernanceTextSupport textSupport,
                           GovernanceAiSupportService governanceAiSupportService) {
        this.governanceSignalRepository = governanceSignalRepository;
        this.governanceSnapshotRepository = governanceSnapshotRepository;
        this.governanceActionRepository = governanceActionRepository;
        this.textSupport = textSupport;
        this.governanceAiSupportService = governanceAiSupportService;
    }

    @Override
    public Map<String, Object> execute(GovernanceEvent event, GovernanceAgentContext context) {
        List<GovernanceSignal> signals = governanceSignalRepository.findByEventIdOrderBySignalTimeAsc(event.getId());
        List<GovernanceSnapshot> snapshots = governanceSnapshotRepository.findByEventIdOrderByVersionNoDesc(event.getId());
        List<GovernanceAction> actions = governanceActionRepository.findByEventIdOrderByCreatedAtDesc(event.getId());

        List<Map<String, Object>> timeline = new ArrayList<>();
        if (!signals.isEmpty()) {
            timeline.add(Map.of("time", String.valueOf(signals.get(0).getSignalTime()), "type", "START",
                    "content", "事件开始被系统纳入治理信号池"));
        }
        for (GovernanceSnapshot snapshot : snapshots.stream()
                .sorted((a, b) -> Integer.compare(a.getVersionNo(), b.getVersionNo()))
                .toList()) {
            timeline.add(Map.of("time", String.valueOf(snapshot.getCreatedAt()), "type", "SNAPSHOT",
                    "content", "生成第 " + snapshot.getVersionNo() + " 版态势快照"));
        }
        for (GovernanceAction action : actions.stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .toList()) {
            timeline.add(Map.of("time", String.valueOf(action.getCreatedAt()), "type", "ACTION",
                    "content", action.getActionType() + " - " + textSupport.abbreviate(action.getActionNote(), 50)));
        }
        if (event.getResolvedAt() != null) {
            timeline.add(Map.of("time", String.valueOf(event.getResolvedAt()), "type", "CLOSE",
                    "content", "事件被关闭或视为已完成处置"));
        }

        String summary = "事件“" + event.getTitle() + "”在 " + signals.size() + " 条信号推动下逐步演化，"
                + "最终形成 " + snapshots.size() + " 版快照和 " + actions.size() + " 条治理动作记录。";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", event.getTitle());
        result.put("eventCode", event.getEventCode());
        result.put("summary", summary);
        result.put("timeline", timeline);
        result.put("governanceActions", actions.stream().map(action -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", action.getActionType());
            item.put("note", action.getActionNote());
            item.put("time", String.valueOf(action.getCreatedAt()));
            item.put("fromAgent", action.getFromAgent());
            return item;
        }).toList());
        result.put("keyTurningPoints", snapshots.isEmpty()
                ? List.of("事件主要停留在早期监测阶段")
                : List.of("风险等级在多轮分析中被持续校正", "管理动作与快照结果共同构成闭环治理记录"));
        result.put("outcome", event.getStatus() + " / " + event.getRiskLevel() + " / " + event.getRumorStatus());
        result.put("lessons", List.of(
                "应尽早沉淀权威证据，降低事件核验成本",
                "对高频负面关键词建立更快的人工复核通道",
                "将治理动作与后续风险变化做持续对照"
        ));
        result.put("aiEnhanced", false);

        if (context.isAiEnabled()) {
            Map<String, Object> aiResult = governanceAiSupportService.completeJson(
                    "You write concise campus governance postmortems. Return JSON only. "
                            + "Use Chinese for all text fields and keep the summary factual.",
                    """
                            Return JSON only with this schema:
                            {
                              "summary":"Chinese summary within 140 chars",
                              "lessons":["up to 3 Chinese strings"]
                            }

                            Event title: %s
                            Event status: %s
                            Risk level: %s
                            Snapshot count: %s
                            Action count: %s
                            """.formatted(event.getTitle(), event.getStatus(), event.getRiskLevel(), snapshots.size(), actions.size()));
            if (!aiResult.isEmpty()) {
                result.put("summary", String.valueOf(aiResult.getOrDefault("summary", result.get("summary"))));
                Object lessons = aiResult.get("lessons");
                if (lessons instanceof List<?> list && !list.isEmpty()) {
                    result.put("lessons", list.stream().map(String::valueOf).toList());
                }
                result.put("aiEnhanced", true);
            }
        }

        return result;
    }
}
