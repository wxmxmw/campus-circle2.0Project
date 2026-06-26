package com.campus.campus_backend.service.governance.agent;

import com.campus.campus_backend.domain.governance.GovernanceEvent;
import com.campus.campus_backend.domain.governance.GovernanceSignal;
import com.campus.campus_backend.repository.governance.GovernanceSignalRepository;
import com.campus.campus_backend.service.governance.GovernanceAiSupportService;
import com.campus.campus_backend.service.governance.GovernanceTextSupport;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SituationAnalysisAgent implements GovernanceAgent<GovernanceEvent, Map<String, Object>> {
    private final GovernanceSignalRepository governanceSignalRepository;
    private final GovernanceTextSupport textSupport;
    private final GovernanceAiSupportService governanceAiSupportService;

    public SituationAnalysisAgent(GovernanceSignalRepository governanceSignalRepository,
                                  GovernanceTextSupport textSupport,
                                  GovernanceAiSupportService governanceAiSupportService) {
        this.governanceSignalRepository = governanceSignalRepository;
        this.textSupport = textSupport;
        this.governanceAiSupportService = governanceAiSupportService;
    }

    @Override
    public Map<String, Object> execute(GovernanceEvent event, GovernanceAgentContext context) {
        List<GovernanceSignal> signals = governanceSignalRepository.findByEventIdOrderBySignalTimeAsc(event.getId());
        int negative = 0;
        int positive = 0;
        int neutral = 0;
        List<String> keywords = new ArrayList<>();
        List<String> demands = new ArrayList<>();
        List<String> highRiskExpressions = new ArrayList<>();
        int posts = 0;
        int comments = 0;
        int reports = 0;
        int totalViewCount = 0;
        int totalLikeCount = 0;

        for (GovernanceSignal signal : signals) {
            if ("POST".equals(signal.getSignalType())) {
                posts++;
            } else if ("COMMENT".equals(signal.getSignalType())) {
                comments++;
            } else if ("REPORT".equals(signal.getSignalType())) {
                reports++;
            }

            Map<String, Object> metadata = textSupport.readJsonObject(signal.getMetadataJson());
            totalViewCount += intValue(metadata.get("viewCount"));
            totalLikeCount += intValue(metadata.get("likeCount"));

            String content = signal.getContentText();
            String sentiment = textSupport.detectSentiment(content);
            switch (sentiment) {
                case "negative" -> negative++;
                case "positive" -> positive++;
                default -> neutral++;
            }
            if (textSupport.containsDemand(content)) {
                demands.add(textSupport.abbreviate(content, 36));
            }
            if (textSupport.containsRumorMarker(content)
                    || "REPORT".equals(signal.getSignalType())
                    || textSupport.hitCount(content, List.of("封校", "停电", "泄题", "事故", "维权")) > 0) {
                highRiskExpressions.add(textSupport.abbreviate(content, 48));
            }

            keywords.addAll(textSupport.extractKeywords(List.of(
                    content,
                    Objects.toString(metadata.get("title"), ""),
                    Objects.toString(metadata.get("relatedTitle"), ""),
                    Objects.toString(metadata.get("postTitle"), "")
            ), 5));
        }

        List<String> dedupKeywords = textSupport.deduplicatePreserveOrder(keywords, 8);
        List<String> dedupDemands = textSupport.deduplicatePreserveOrder(demands, 5);
        List<String> dedupRisk = textSupport.deduplicatePreserveOrder(highRiskExpressions, 5);
        BigDecimal negativeRatio = textSupport.ratio(negative, signals.size());

        int riskScore = Math.min(100, reports * 18 + comments * 4 + posts * 8 + negative * 10
                + Math.min(20, totalViewCount / 30) + Math.min(12, totalLikeCount / 10));
        String riskLevel = riskScore >= 75 ? "HIGH" : riskScore >= 40 ? "MEDIUM" : "LOW";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keywords", dedupKeywords);
        result.put("userDemands", dedupDemands.isEmpty()
                ? List.of("希望管理方及时说明情况并持续反馈")
                : dedupDemands);
        result.put("highRiskExpressions", dedupRisk);
        result.put("negativeRatio", negativeRatio);
        result.put("stanceDistribution", Map.of("support", positive, "oppose", negative, "neutral", neutral));
        result.put("counts", Map.of("posts", posts, "comments", comments, "reports", reports));
        result.put("riskScore", riskScore);
        result.put("riskLevel", riskLevel);
        result.put("opinionSummary", buildOpinionSummary(event, dedupKeywords, negative, positive, neutral, reports));
        result.put("demandsSummary", dedupDemands.isEmpty()
                ? "用户核心诉求集中在尽快获得解释、明确处理进度和降低信息不透明带来的焦虑。"
                : "当前较高频的诉求包括：" + String.join("；", dedupDemands));
        result.put("heatDescription", buildHeatDescription(signals.size(), reports, negativeRatio, totalViewCount));
        result.put("aiEnhanced", false);

        if (context.isAiEnabled()) {
            Map<String, Object> aiResult = governanceAiSupportService.completeJson(
                    "You analyze campus public-opinion events. Return JSON only. "
                            + "Use Chinese for all text fields. Keep the result concise and grounded in the supplied signals.",
                    """
                            Return JSON only with this schema:
                            {
                              "opinionSummary":"Chinese summary within 120 chars",
                              "demandsSummary":"Chinese summary within 100 chars",
                              "userDemands":["up to 4 Chinese strings"],
                              "highRiskExpressions":["up to 4 Chinese strings"]
                            }

                            Focus on:
                            - the main dissatisfaction
                            - the most repeated user demands
                            - the phrases most likely to escalate sentiment

                            Event title: %s
                            Keywords: %s
                            Risk level: %s
                            Risk score: %s
                            Sample signals:
                            %s
                            """.formatted(
                            event.getTitle(),
                            dedupKeywords,
                            riskLevel,
                            riskScore,
                            signals.stream().limit(8).map(GovernanceSignal::getContentText).toList()));
            if (!aiResult.isEmpty()) {
                result.put("opinionSummary", stringValue(aiResult.get("opinionSummary"), String.valueOf(result.get("opinionSummary"))));
                result.put("demandsSummary", stringValue(aiResult.get("demandsSummary"), String.valueOf(result.get("demandsSummary"))));
                result.put("userDemands", readStringList(aiResult.get("userDemands"), (List<String>) result.get("userDemands")));
                result.put("highRiskExpressions", readStringList(aiResult.get("highRiskExpressions"), dedupRisk));
                result.put("aiEnhanced", true);
            }
        }

        context.putTrace("situationSignals", signals.size());
        context.putTrace("situationRiskScore", riskScore);
        return result;
    }

    private String buildOpinionSummary(GovernanceEvent event, List<String> keywords, int negative, int positive,
                                       int neutral, int reports) {
        String mood = negative >= positive && negative >= neutral
                ? "负面情绪占主导"
                : positive >= negative && positive >= neutral
                ? "支持或正向表达更多"
                : "整体讨论偏中性";
        String topic = keywords.isEmpty() ? event.getTitle() : String.join("、", keywords);
        return "围绕“" + event.getTitle() + "”的讨论主要集中在" + topic + "，当前" + mood
                + "。举报量为" + reports + "，说明事件已从普通讨论转向治理关注。";
    }

    private String buildHeatDescription(int totalSignals, int reports, BigDecimal negativeRatio, int viewCount) {
        return "共纳入 " + totalSignals + " 条信号，举报 " + reports + " 条，负面占比 "
                + negativeRatio + "%，相关内容累计浏览约 " + viewCount + " 次。";
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private String stringValue(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(Object value, List<String> fallback) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return fallback;
    }
}
