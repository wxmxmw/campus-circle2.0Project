package com.campus.campus_backend.service;

import com.campus.campus_backend.vo.AiReviewHitVO;
import com.campus.campus_backend.vo.AiReviewResultVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AiContentReviewService {
    private static final Logger log = LoggerFactory.getLogger(AiContentReviewService.class);

    private final ObjectMapper objectMapper;
    private final List<TermRule> termRules = new ArrayList<>();
    private final List<RegexRule> regexRules = new ArrayList<>();

    @Value("${ai.review.enabled:true}")
    private boolean enabled;

    @Value("${ai.review.semantic-check.enabled:true}")
    private boolean semanticCheckEnabled;

    @Value("${ai.review.semantic-check.model:gpt-4.1-mini}")
    private String semanticModel;

    @Value("${ai.review.threshold.medium:2}")
    private int mediumThreshold;

    @Value("${ai.openai.api-key:}")
    private String apiKey;

    @Value("${ai.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${ai.openai.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${ai.openai.request-timeout-seconds:30}")
    private int requestTimeoutSeconds;

    @Value("${ai.openai.proxy-host:}")
    private String proxyHost;

    @Value("${ai.openai.proxy-port:0}")
    private int proxyPort;

    public AiContentReviewService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initRules() {
        loadTermRules();
        loadRegexRules();
        log.info("AI review rules loaded: {} term rules, {} regex rules", termRules.size(), regexRules.size());
    }

    public AiReviewResultVO review(String title, String polishedContent, List<String> tags) {
        if (!enabled) {
            return lowRisk("Review is disabled.");
        }

        String fullText = (Objects.toString(title, "") + "\n" + Objects.toString(polishedContent, "") + "\n"
                + String.join(" ", tags == null ? List.of() : tags)).trim();
        if (fullText.isBlank()) {
            return lowRisk("No content to review.");
        }

        List<AiReviewHitVO> hits = scanRules(fullText);
        String ruleRisk = decideRuleRisk(hits);
        SemanticResult semantic = semanticCheckEnabled ? callSemanticReview(fullText, hits) : null;
        String finalRisk = mergeRisk(ruleRisk, semantic, hits.size());

        Set<String> categories = new LinkedHashSet<>();
        for (AiReviewHitVO hit : hits) {
            categories.add(hit.getCategory());
        }
        if (semantic != null) {
            categories.addAll(semantic.categories());
        }
        if (categories.isEmpty()) {
            categories.add("normal");
        }

        String suggestion = buildSuggestion(finalRisk, categories, semantic);
        boolean canUseDirectly = !"high".equals(finalRisk);
        return new AiReviewResultVO(finalRisk, new ArrayList<>(categories), hits, suggestion, canUseDirectly);
    }

    private List<AiReviewHitVO> scanRules(String text) {
        List<AiReviewHitVO> hits = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        for (TermRule rule : termRules) {
            if (lower.contains(rule.termLower())) {
                hits.add(new AiReviewHitVO("term", rule.category(), rule.term(), rule.severity()));
            }
        }
        for (RegexRule rule : regexRules) {
            if (rule.pattern().matcher(text).find()) {
                hits.add(new AiReviewHitVO("regex", rule.category(), rule.name(), rule.severity()));
            }
        }
        return hits;
    }

    private String decideRuleRisk(List<AiReviewHitVO> hits) {
        if (hits.isEmpty()) {
            return "low";
        }
        int high = 0;
        int medium = 0;
        for (AiReviewHitVO hit : hits) {
            if ("high".equals(hit.getSeverity())) {
                high++;
            } else if ("medium".equals(hit.getSeverity())) {
                medium++;
            }
        }
        if (high > 0) {
            return "high";
        }
        if (medium >= Math.max(mediumThreshold, 1)) {
            return "medium";
        }
        return "low";
    }

    private SemanticResult callSemanticReview(String text, List<AiReviewHitVO> hits) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        try {
            String prompt = """
                    You are a safety classifier for Chinese campus community content.
                    Classify risk and return JSON only:
                    {"riskLevel":"low|medium|high","categories":["abuse|discrimination|illegal|normal"],"reason":"..."}

                    Rules:
                    - high: obvious abuse/discrimination/illegal guidance.
                    - medium: potentially risky tone or implied harmful intent.
                    - low: normal discussion/help-sharing.

                    Text:
                    %s

                    Rule hits summary:
                    %s
                    """.formatted(text, toJson(hits));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", normalizeModel(semanticModel));
            body.put("temperature", 0.0);
            body.put("max_tokens", 400);
            body.put("response_format", Map.of("type", "json_object"));
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "You are a safety classifier. Return valid JSON only."),
                    Map.of("role", "user", "content", prompt)
            ));

            String endpoint = trimTrailingSlash(baseUrl) + "/chat/completions";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = buildHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("semantic review skipped due to non-2xx: {}", response.statusCode());
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            String textResp = root.path("choices").path(0).path("message").path("content").asText("");
            if (textResp.isBlank()) {
                return null;
            }
            JsonNode obj = objectMapper.readTree(extractJson(textResp));
            String risk = normalizeRisk(obj.path("riskLevel").asText("low"));
            List<String> categories = new ArrayList<>();
            JsonNode arr = obj.path("categories");
            if (arr.isArray()) {
                for (JsonNode item : arr) {
                    String c = item.asText("").trim();
                    if (!c.isEmpty()) categories.add(c);
                }
            }
            String reason = obj.path("reason").asText("");
            return new SemanticResult(risk, categories, reason);
        } catch (Exception ex) {
            log.warn("semantic review failed: {}", ex.getMessage());
            return null;
        }
    }

    private String mergeRisk(String ruleRisk, SemanticResult semantic, int hitCount) {
        if (semantic == null) {
            return ruleRisk;
        }
        int ruleRank = riskRank(ruleRisk);
        int semanticRank = riskRank(semantic.riskLevel());
        if (ruleRank == 2 && semanticRank == 1 && hitCount <= 2) {
            return "low";
        }
        return semanticRank > ruleRank ? semantic.riskLevel() : ruleRisk;
    }

    private String buildSuggestion(String risk, Set<String> categories, SemanticResult semantic) {
        if ("high".equals(risk)) {
            return "Potential high-risk content detected. Please remove abusive, discriminatory, or illegal guidance and regenerate.";
        }
        if ("medium".equals(risk)) {
            return "Potential risk detected. Please revise the tone to be objective, polite, and compliant.";
        }
        if (semantic != null && semantic.reason() != null && !semantic.reason().isBlank()) {
            return "Overall risk is low. You can use it directly or fine-tune manually.";
        }
        if (categories.contains("normal")) {
            return "Risk is low. You can use this content directly.";
        }
        return "Content passed basic review. You can publish after optional edits.";
    }

    private AiReviewResultVO lowRisk(String suggestion) {
        return new AiReviewResultVO("low", List.of("normal"), List.of(), suggestion, true);
    }

    private void loadTermRules() {
        termRules.clear();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource("ai-review-sensitive-terms.txt").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split("\\|", 3);
                if (parts.length < 3) {
                    continue;
                }
                termRules.add(new TermRule(parts[0].trim(), normalizeRisk(parts[1].trim()), parts[2].trim()));
            }
        } catch (Exception ex) {
            log.warn("failed to load ai-review-sensitive-terms.txt: {}", ex.getMessage());
        }
    }

    private void loadRegexRules() {
        regexRules.clear();
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource("ai-review-patterns.json").getInputStream());
            if (!root.isArray()) {
                return;
            }
            for (JsonNode item : root) {
                String name = item.path("name").asText("");
                String category = item.path("category").asText("illegal");
                String severity = normalizeRisk(item.path("severity").asText("medium"));
                String regex = item.path("regex").asText("");
                if (name.isBlank() || regex.isBlank()) {
                    continue;
                }
                regexRules.add(new RegexRule(name, category, severity, Pattern.compile(regex, Pattern.CASE_INSENSITIVE)));
            }
        } catch (Exception ex) {
            log.warn("failed to load ai-review-patterns.json: {}", ex.getMessage());
        }
    }

    private HttpClient buildHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(connectTimeoutSeconds, 1)));
        if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost.trim(), proxyPort)));
        }
        return builder.build();
    }

    private String normalizeRisk(String value) {
        String risk = value == null ? "low" : value.trim().toLowerCase(Locale.ROOT);
        if (!risk.equals("low") && !risk.equals("medium") && !risk.equals("high")) {
            return "low";
        }
        return risk;
    }

    private int riskRank(String risk) {
        return switch (normalizeRisk(risk)) {
            case "high" -> 3;
            case "medium" -> 2;
            default -> 1;
        };
    }

    private String normalizeModel(String value) {
        return value == null || value.isBlank() ? "gpt-4.1-mini" : value.trim();
    }

    private String trimTrailingSlash(String value) {
        String result = value == null || value.isBlank()
                ? "https://api.openai.com/v1"
                : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String extractJson(String text) {
        String trimmed = text == null ? "" : text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private record TermRule(String category, String severity, String term) {
        private String termLower() {
            return term.toLowerCase(Locale.ROOT);
        }
    }

    private record RegexRule(String name, String category, String severity, Pattern pattern) {}

    private record SemanticResult(String riskLevel, List<String> categories, String reason) {}
}
