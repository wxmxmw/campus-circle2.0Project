package com.campus.campus_backend.service.governance.agent;

import com.campus.campus_backend.domain.AggregatedNotice;
import com.campus.campus_backend.domain.governance.GovernanceEvent;
import com.campus.campus_backend.domain.governance.GovernanceEvidenceRef;
import com.campus.campus_backend.domain.governance.GovernanceSignal;
import com.campus.campus_backend.repository.AggregatedNoticeRepository;
import com.campus.campus_backend.repository.governance.GovernanceEvidenceRefRepository;
import com.campus.campus_backend.repository.governance.GovernanceSignalRepository;
import com.campus.campus_backend.service.governance.GovernanceAiSupportService;
import com.campus.campus_backend.service.governance.GovernanceTextSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RumorVerificationAgent implements GovernanceAgent<GovernanceEvent, Map<String, Object>> {
    private static final List<String> FACTUAL_MARKERS = List.of(
            "通知", "封校", "停课", "停电", "成绩", "处分", "事故", "泄题", "搬离", "提前放假"
    );

    private final GovernanceSignalRepository governanceSignalRepository;
    private final AggregatedNoticeRepository aggregatedNoticeRepository;
    private final GovernanceEvidenceRefRepository governanceEvidenceRefRepository;
    private final GovernanceAiSupportService governanceAiSupportService;
    private final GovernanceTextSupport textSupport;

    public RumorVerificationAgent(GovernanceSignalRepository governanceSignalRepository,
                                  AggregatedNoticeRepository aggregatedNoticeRepository,
                                  GovernanceEvidenceRefRepository governanceEvidenceRefRepository,
                                  GovernanceAiSupportService governanceAiSupportService,
                                  GovernanceTextSupport textSupport) {
        this.governanceSignalRepository = governanceSignalRepository;
        this.aggregatedNoticeRepository = aggregatedNoticeRepository;
        this.governanceEvidenceRefRepository = governanceEvidenceRefRepository;
        this.governanceAiSupportService = governanceAiSupportService;
        this.textSupport = textSupport;
    }

    @Override
    @Transactional
    public Map<String, Object> execute(GovernanceEvent event, GovernanceAgentContext context) {
        List<GovernanceSignal> signals = governanceSignalRepository.findByEventIdOrderBySignalTimeAsc(event.getId());
        List<String> claims = new ArrayList<>();
        List<String> keywords = new ArrayList<>();
        for (GovernanceSignal signal : signals) {
            String content = signal.getContentText();
            if (textSupport.containsRumorMarker(content) || textSupport.hitCount(content, FACTUAL_MARKERS) > 0) {
                claims.add(textSupport.abbreviate(content, 80));
            }
            keywords.addAll(textSupport.extractKeywords(List.of(content), 4));
        }

        List<String> dedupClaims = textSupport.deduplicatePreserveOrder(claims, 5);
        List<String> dedupKeywords = textSupport.deduplicatePreserveOrder(keywords, 8);
        List<AggregatedNotice> evidencePool = aggregatedNoticeRepository
                .findTop20ByStatusOrderByPublishTimeDescCreatedAtDesc("ONLINE");
        List<Map<String, Object>> evidenceList = new ArrayList<>();
        List<String> evidenceSnippets = new ArrayList<>();
        int bestSupportScore = 0;

        governanceEvidenceRefRepository.deleteByEventId(event.getId());
        for (AggregatedNotice notice : evidencePool) {
            int score = overlapScore(dedupKeywords, List.of(
                    Objects.toString(notice.getTitle(), ""),
                    Objects.toString(notice.getSummary(), ""),
                    Objects.toString(notice.getContentSnapshot(), "")
            ));
            if (score <= 0) {
                continue;
            }
            bestSupportScore = Math.max(bestSupportScore, score);

            GovernanceEvidenceRef ref = new GovernanceEvidenceRef();
            ref.setEvent(event);
            ref.setEvidenceType("INFO_NOTICE");
            ref.setRefId(notice.getId());
            ref.setTitle(notice.getTitle());
            ref.setExcerpt(textSupport.abbreviate(
                    Objects.toString(notice.getSummary(), Objects.toString(notice.getContentSnapshot(), "")), 140));
            ref.setConfidence(BigDecimal.valueOf(Math.min(100, score * 25L)));
            governanceEvidenceRefRepository.save(ref);

            Map<String, Object> evidenceMap = new LinkedHashMap<>();
            evidenceMap.put("id", notice.getId());
            evidenceMap.put("type", "INFO_NOTICE");
            evidenceMap.put("title", notice.getTitle());
            evidenceMap.put("excerpt", ref.getExcerpt());
            evidenceMap.put("confidence", ref.getConfidence());
            evidenceList.add(evidenceMap);
            evidenceSnippets.add("[" + notice.getId() + "] "
                    + Objects.toString(notice.getTitle(), "") + " :: " + ref.getExcerpt());
        }

        String rumorStatus = baselineRumorStatus(dedupClaims, bestSupportScore);
        String conclusion = baselineConclusion(rumorStatus, dedupClaims.isEmpty());
        List<String> conflictPoints = baselineConflictPoints(rumorStatus, bestSupportScore);
        int confidence = Math.min(100, bestSupportScore * 25);
        boolean aiEnhanced = false;

        if (context.isAiEnabled() && !dedupClaims.isEmpty() && governanceAiSupportService.isAvailable()) {
            Map<String, Object> aiResult = governanceAiSupportService.completeJson(
                    "You verify factual claims inside campus public-opinion events. Return JSON only. "
                            + "Be conservative and do not invent evidence. "
                            + "Use Chinese for all text fields. rumorStatus must be one of "
                            + "CONFIRMED, UNVERIFIED, INSUFFICIENT, REFUTED.",
                    """
                            Return JSON only with this schema:
                            {
                              "rumorStatus":"CONFIRMED|UNVERIFIED|INSUFFICIENT|REFUTED",
                              "conclusion":"Chinese summary within 90 chars",
                              "coreClaims":["up to 3 Chinese strings"],
                              "conflictPoints":["up to 3 Chinese strings"],
                              "confidence":0
                            }

                            Decision rules:
                            - CONFIRMED: official evidence clearly supports the main factual claim.
                            - REFUTED: official evidence clearly contradicts the main factual claim.
                            - INSUFFICIENT: there is a concrete claim, but evidence is missing or too weak.
                            - UNVERIFIED: the discussion exists, but the claim is still ambiguous or not concrete enough.

                            Event title: %s
                            Candidate claims: %s
                            Keywords: %s
                            Matched official evidence:
                            %s
                            Rule-based baseline status: %s
                            Rule-based baseline conclusion: %s
                            """.formatted(
                            event.getTitle(),
                            dedupClaims,
                            dedupKeywords,
                            evidenceSnippets.isEmpty() ? "NONE" : String.join("\n", evidenceSnippets),
                            rumorStatus,
                            conclusion));
            if (!aiResult.isEmpty()) {
                rumorStatus = normalizeRumorStatus(aiResult.get("rumorStatus"), rumorStatus);
                conclusion = stringValue(aiResult.get("conclusion"), conclusion);
                dedupClaims = readStringList(aiResult.get("coreClaims"), dedupClaims);
                conflictPoints = readStringList(aiResult.get("conflictPoints"), conflictPoints);
                confidence = intValue(aiResult.get("confidence"), confidence);
                aiEnhanced = true;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rumorStatus", rumorStatus);
        result.put("conclusion", conclusion);
        result.put("coreClaims", dedupClaims);
        result.put("evidence", evidenceList);
        result.put("confidence", confidence);
        result.put("conflictPoints", conflictPoints);
        result.put("aiEnhanced", aiEnhanced);
        context.putTrace("rumorClaims", dedupClaims.size());
        context.putTrace("rumorStatus", rumorStatus);
        context.putTrace("rumorAiEnhanced", aiEnhanced);
        return result;
    }

    private String baselineRumorStatus(List<String> claims, int bestSupportScore) {
        if (claims.isEmpty()) {
            return "UNVERIFIED";
        }
        if (bestSupportScore >= 3) {
            return "CONFIRMED";
        }
        if (bestSupportScore == 0) {
            return "INSUFFICIENT";
        }
        return "UNVERIFIED";
    }

    private String baselineConclusion(String rumorStatus, boolean noClaims) {
        if (noClaims) {
            return "当前讨论以情绪表达和治理诉求为主，尚未抽取到需要重点核验的明确事实说法。";
        }
        return switch (rumorStatus) {
            case "CONFIRMED" -> "已找到较强的校内权威信息关联，核心说法存在被正式通知或公开信息支持的迹象。";
            case "INSUFFICIENT" -> "存在事实性说法，但暂未检索到足够的校内权威证据支撑或反驳，建议管理方补充说明。";
            default -> "存在部分相关证据，但不足以形成明确结论，建议继续观察并补充人工核验。";
        };
    }

    private List<String> baselineConflictPoints(String rumorStatus, int bestSupportScore) {
        if ("INSUFFICIENT".equals(rumorStatus) && bestSupportScore == 0) {
            return List.of("当前暂无明确权威说明与传播说法建立稳定对应关系。");
        }
        return List.of();
    }

    private int overlapScore(List<String> keywords, List<String> texts) {
        int score = 0;
        for (String keyword : keywords) {
            for (String text : texts) {
                if (text != null && text.contains(keyword)) {
                    score++;
                    break;
                }
            }
        }
        return score;
    }

    private String normalizeRumorStatus(Object value, String fallback) {
        String text = stringValue(value, fallback).toUpperCase();
        return switch (text) {
            case "CONFIRMED", "UNVERIFIED", "INSUFFICIENT", "REFUTED" -> text;
            default -> fallback;
        };
    }

    private String stringValue(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private List<String> readStringList(Object value, List<String> fallback) {
        if (value instanceof List<?> list) {
            List<String> mapped = list.stream()
                    .map(String::valueOf)
                    .map(textSupport::normalizeText)
                    .filter(item -> !item.isBlank())
                    .limit(3)
                    .toList();
            if (!mapped.isEmpty()) {
                return mapped;
            }
        }
        return fallback;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(0, Math.min(100, number.intValue()));
        }
        if (value != null) {
            try {
                return Math.max(0, Math.min(100, Integer.parseInt(String.valueOf(value).trim())));
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }
}
