package com.campus.campus_backend.service.governance.agent;

import com.campus.campus_backend.domain.governance.GovernanceEvent;
import com.campus.campus_backend.service.governance.GovernanceAiSupportService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GovernanceSuggestionAgent implements GovernanceAgent<GovernanceEvent, Map<String, Object>> {
    private final GovernanceAiSupportService governanceAiSupportService;

    public GovernanceSuggestionAgent(GovernanceAiSupportService governanceAiSupportService) {
        this.governanceAiSupportService = governanceAiSupportService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(GovernanceEvent event, GovernanceAgentContext context) {
        Map<String, Object> situation = context.getTrace().containsKey("situation")
                ? (Map<String, Object>) context.getTrace().get("situation") : Map.of();
        Map<String, Object> rumor = context.getTrace().containsKey("rumor")
                ? (Map<String, Object>) context.getTrace().get("rumor") : Map.of();

        String riskLevel = String.valueOf(situation.getOrDefault("riskLevel", event.getRiskLevel()));
        int riskScore = intValue(situation.get("riskScore"), event.getRiskScore() == null ? 0 : event.getRiskScore());
        String rumorStatus = String.valueOf(rumor.getOrDefault("rumorStatus", event.getRumorStatus()));

        List<Map<String, Object>> actions = new ArrayList<>();
        if ("HIGH".equals(riskLevel)) {
            actions.add(action("ESCALATE_ADMIN", 1, "高风险事件，需尽快升级到高权限管理员或辅导员处理。"));
            actions.add(action("MANUAL_REVIEW", 2, "优先复核核心帖子、评论链和集中举报内容。"));
        }
        if ("REFUTED".equals(rumorStatus) || "INSUFFICIENT".equals(rumorStatus)) {
            actions.add(action("CLARIFY", actions.isEmpty() ? 1 : 3, "存在事实争议，建议发布澄清或补充说明。"));
        }
        if (riskScore >= 50) {
            actions.add(action("FAST_HANDLE_REPORTS", actions.isEmpty() ? 1 : 4, "相关举报较集中，建议加速核查处置。"));
        }
        if (actions.isEmpty()) {
            actions.add(action("OBSERVE", 1, "当前风险可控，建议持续观察后续新增讨论。"));
        }

        List<String> reviewPoints = new ArrayList<>();
        if ("INSUFFICIENT".equals(rumorStatus) || "UNVERIFIED".equals(rumorStatus)) {
            reviewPoints.add("核对是否已有校内权威通知可作为正式证据。");
        }
        if ("HIGH".equals(riskLevel)) {
            reviewPoints.add("关注评论区是否出现人身攻击、集体动员或跨群扩散。");
        }
        reviewPoints.add("确认相关发帖人是否愿意补充事实细节。");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("riskLevel", riskLevel);
        result.put("riskScore", riskScore);
        result.put("recommendedActions", actions);
        result.put("humanReviewPoints", reviewPoints);
        result.put("recommendedMessage", buildMessage(event.getTitle(), riskLevel, rumorStatus));
        result.put("aiEnhanced", false);

        if (context.isAiEnabled()) {
            Map<String, Object> aiResult = governanceAiSupportService.completeJson(
                    "You are a campus governance response planner. Return JSON only. "
                            + "Use Chinese for all text fields and keep advice practical, cautious, and actionable.",
                    """
                            Return JSON only with this schema:
                            {
                              "recommendedMessage":"Chinese message within 120 chars",
                              "humanReviewPoints":["up to 4 Chinese strings"]
                            }

                            Event title: %s
                            Risk level: %s
                            Rumor status: %s
                            Recommended actions: %s
                            """.formatted(event.getTitle(), riskLevel, rumorStatus, actions));
            if (!aiResult.isEmpty()) {
                result.put("recommendedMessage",
                        String.valueOf(aiResult.getOrDefault("recommendedMessage", result.get("recommendedMessage"))));
                Object points = aiResult.get("humanReviewPoints");
                if (points instanceof List<?> list && !list.isEmpty()) {
                    result.put("humanReviewPoints", list.stream().map(String::valueOf).toList());
                }
                result.put("aiEnhanced", true);
            }
        }

        context.putTrace("suggestionCount", actions.size());
        return result;
    }

    private Map<String, Object> action(String type, int priority, String reason) {
        return Map.of("type", type, "priority", priority, "reason", reason);
    }

    private String buildMessage(String title, String riskLevel, String rumorStatus) {
        if ("HIGH".equals(riskLevel)) {
            return "事件“" + title + "”已进入高风险区间，建议先控节奏、再核信息、最后统一口径发布说明。";
        }
        if ("INSUFFICIENT".equals(rumorStatus)) {
            return "事件“" + title + "”存在事实争议，建议补充权威信息并尽快给出明确回应。";
        }
        return "事件“" + title + "”当前总体可控，建议保留观察并记录后续扩散情况。";
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }
}
