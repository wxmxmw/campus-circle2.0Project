package com.campus.campus_backend.service;

import com.campus.campus_backend.common.BizException;
import com.campus.campus_backend.common.ErrorCode;
import com.campus.campus_backend.domain.Category;
import com.campus.campus_backend.dto.PostAssistRequest;
import com.campus.campus_backend.repository.CategoryRepository;
import com.campus.campus_backend.vo.AiReviewResultVO;
import com.campus.campus_backend.vo.PostAssistResponseVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeminiPostAssistService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GeminiPostAssistService.class);
    private static final Pattern RECOMMENDED_CATEGORY_PATTERN =
            Pattern.compile("\"recommendedCategoryId\"\\s*:\\s*(\\d+|\"\\d+\")", Pattern.DOTALL);
    private static final Pattern TAGS_ARRAY_PATTERN =
            Pattern.compile("\"tags\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern QUOTED_VALUE_PATTERN =
            Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);
    private static final List<String> FALLBACK_TOPIC_TAGS = List.of(
            "\u671f\u672b", "\u8003\u8bd5", "\u8bfe\u7a0b", "\u9009\u8bfe", "\u4f5c\u4e1a",
            "\u6210\u7ee9", "\u5bbf\u820d", "\u98df\u5802", "\u56fe\u4e66\u9986", "\u5b9e\u4e60",
            "\u517c\u804c", "\u793e\u56e2", "\u8003\u7814", "\u6821\u56ed"
    );
    private static final List<String> FALLBACK_GENERIC_TAGS = List.of(
            "\u8ba8\u8bba", "\u6c42\u52a9", "\u6821\u56ed\u751f\u6d3b"
    );

    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;
    private final AiContentReviewService aiContentReviewService;

    @Value("${ai.openai.api-key:}")
    private String apiKey;

    @Value("${ai.openai.model:gpt-4.1-mini}")
    private String model;

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

    public GeminiPostAssistService(CategoryRepository categoryRepository, ObjectMapper objectMapper,
                                   AiContentReviewService aiContentReviewService) {
        this.categoryRepository = categoryRepository;
        this.objectMapper = objectMapper;
        this.aiContentReviewService = aiContentReviewService;
    }

    public PostAssistResponseVO assist(PostAssistRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "Please configure OPENAI_API_KEY before using AI post assistant");
        }

        List<Category> categories = categoryRepository.findAll().stream()
                .filter(category -> !Boolean.FALSE.equals(category.getIsActive()))
                .toList();
        if (categories.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "No category is configured for topic recommendation");
        }

        String prompt = buildPrompt(request, categories);
        String rawText = callOpenAiCompatible(prompt);
        Map<String, Object> ai = parseJsonObject(rawText);

        String title = firstNonBlank(
                normalizeText(ai.get("title"), 40),
                fallbackTitle(request.getContent())
        );
        String polishedContent = firstNonBlank(
                normalizeText(ai.get("polishedContent"), 2000),
                fallbackPolishedContent(request.getContent())
        );

        Category recommended = resolveCategory(ai.get("recommendedCategoryId"), request, categories, title, polishedContent);
        List<String> tags = ensureMinimumTags(normalizeTags(ai.get("tags")), recommended.getName(), title, polishedContent);

        AiReviewResultVO review;
        try {
            review = aiContentReviewService.review(title, polishedContent, tags);
        } catch (Exception ex) {
            log.warn("ai review degraded: {}", ex.getMessage());
            review = new AiReviewResultVO("low", List.of("normal"), List.of(),
                    "Review temporarily unavailable. You can continue editing manually.", true);
        }

        log.info("ai-post-review riskLevel={}, categories={}, hitCount={}",
                review.getRiskLevel(), review.getCategories(), review.getHits() == null ? 0 : review.getHits().size());

        return new PostAssistResponseVO(title, polishedContent, recommended.getId(), recommended.getName(), tags, review);
    }

    private String buildPrompt(PostAssistRequest request, List<Category> categories) {
        List<Map<String, Object>> categoryPayload = categories.stream()
                .map(category -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", category.getId());
                    item.put("name", category.getName());
                    return item;
                })
                .toList();

        return """
                You are an AI writing assistant for a Chinese campus social app.
                Based on the user's draft, produce a concise post title, a clear and polite polished post body,
                recommend exactly one category from the provided existing category list, and generate 3 to 5 tags.

                Rules:
                - Return exactly one JSON object only. Do not use Markdown or code fences.
                - Keep the entire JSON on a single line.
                - JSON shape:
                  {"title":"...","polishedContent":"...","recommendedCategoryId":1,"tags":["...","...","..."]}
                - recommendedCategoryId must be one of the provided category ids.
                - Write the title and polishedContent in natural English.
                - polishedContent should preserve the user's meaning and not invent facts.
                - Escape line breaks inside JSON strings.
                - tags should be short English keywords without "#".

                Existing categories:
                %s

                User selected category id:
                %s

                User draft:
                %s
                """.formatted(toJson(categoryPayload), Objects.toString(request.getCategoryId(), "null"),
                request.getContent());
    }

    String callOpenAiCompatible(String prompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", normalizeModel(model));
            body.put("temperature", 0.2);
            body.put("max_tokens", 1200);
            body.put("response_format", Map.of("type", "json_object"));
            body.put("messages", List.of(
                    Map.of("role", "system", "content",
                            "You are a precise assistant. Return exactly one valid single-line JSON object only."),
                    Map.of("role", "user", "content", prompt)
            ));

            String endpoint = trimTrailingSlash(baseUrl) + "/chat/completions";
            log.info("ai-post-assist request model={}, endpoint={}, timeout={}s",
                    normalizeModel(model), endpoint, requestTimeoutSeconds);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpClient httpClient = buildHttpClient();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(ErrorCode.SERVER_ERROR.getCode(),
                        "OpenAI-compatible request failed: " + readOpenAiError(response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("choices").path(0).path("message").path("content").asText("");
            if (text.isBlank()) {
                throw new BizException(ErrorCode.SERVER_ERROR.getCode(), "AI returned empty content");
            }
            return text;
        } catch (BizException ex) {
            throw ex;
        } catch (HttpConnectTimeoutException ex) {
            throw new BizException(ErrorCode.SERVER_ERROR.getCode(),
                    "Cannot connect to AI endpoint. Check OPENAI_BASE_URL or configure ai.openai.proxy-host/port.");
        } catch (HttpTimeoutException ex) {
            throw new BizException(ErrorCode.SERVER_ERROR.getCode(),
                    "AI request timed out. model=" + normalizeModel(model)
                            + ", endpoint=" + trimTrailingSlash(baseUrl)
                            + ", timeout=" + requestTimeoutSeconds + "s");
        } catch (ConnectException ex) {
            throw new BizException(ErrorCode.SERVER_ERROR.getCode(),
                    "Connection to AI endpoint was refused or blocked. Check network or configure ai.openai.proxy-host/port.");
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SERVER_ERROR.getCode(), "AI request error: " + ex.getMessage());
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String text) {
        for (String candidate : buildJsonCandidates(text)) {
            try {
                Object value = objectMapper.readValue(sanitizeJsonCandidate(candidate), Object.class);
                if (value instanceof Map<?, ?> map) {
                    return (Map<String, Object>) map;
                }
                if (value instanceof String str) {
                    String nested = extractJson(str);
                    Object nestedValue = objectMapper.readValue(sanitizeJsonCandidate(nested), Object.class);
                    if (nestedValue instanceof Map<?, ?> nestedMap) {
                        return (Map<String, Object>) nestedMap;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        Map<String, Object> recovered = recoverJsonObject(text);
        if (!recovered.isEmpty()) {
            log.warn("ai json parse degraded to field recovery, raw response={}", abbreviate(text, 600));
            return recovered;
        }

        log.warn("ai json parse failed, raw response={}", abbreviate(text, 600));
        throw new BizException(ErrorCode.SERVER_ERROR.getCode(), "AI returned unusable content");
    }

    private List<String> buildJsonCandidates(String text) {
        List<String> candidates = new ArrayList<>();
        String raw = text == null ? "" : text.trim();
        if (!raw.isBlank()) {
            candidates.add(raw);
        }

        String unfenced = raw
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();
        if (!unfenced.isBlank() && !candidates.contains(unfenced)) {
            candidates.add(unfenced);
        }

        String extracted = extractJson(unfenced);
        if (!extracted.isBlank() && !candidates.contains(extracted)) {
            candidates.add(extracted);
        }

        String unescaped = unfenced
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .trim();
        if (!unescaped.isBlank() && !candidates.contains(unescaped)) {
            candidates.add(unescaped);
        }

        String extractedUnescaped = extractJson(unescaped);
        if (!extractedUnescaped.isBlank() && !candidates.contains(extractedUnescaped)) {
            candidates.add(extractedUnescaped);
        }

        return candidates;
    }

    private String sanitizeJsonCandidate(String text) {
        String raw = text == null ? "" : text.trim();
        if (raw.isBlank()) {
            return raw;
        }

        StringBuilder result = new StringBuilder(raw.length() + 16);
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (escaping) {
                result.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                result.append(ch);
                escaping = true;
                continue;
            }
            if (ch == '"') {
                result.append(ch);
                inString = !inString;
                continue;
            }
            if (inString) {
                if (ch == '\n') {
                    result.append("\\n");
                    continue;
                }
                if (ch == '\r') {
                    result.append("\\r");
                    continue;
                }
                if (ch == '\t') {
                    result.append("\\t");
                    continue;
                }
            }
            result.append(ch);
        }
        return result.toString();
    }

    private Map<String, Object> recoverJsonObject(String text) {
        Map<String, Object> recovered = new LinkedHashMap<>();
        String normalized = text == null ? "" : text.trim();

        ExtractedText title = extractJsonStringField(normalized, "title", List.of("polishedContent", "recommendedCategoryId", "tags"));
        ExtractedText polishedContent = extractJsonStringField(normalized, "polishedContent", List.of("recommendedCategoryId", "tags"));
        Long recommendedCategoryId = extractRecommendedCategoryId(normalized);
        List<String> tags = extractTags(normalized);

        if (title.complete() && !title.value().isBlank()) {
            recovered.put("title", title.value());
        }
        if (polishedContent.complete() && !polishedContent.value().isBlank()) {
            recovered.put("polishedContent", polishedContent.value());
        }
        if (recommendedCategoryId != null) {
            recovered.put("recommendedCategoryId", recommendedCategoryId);
        }
        if (!tags.isEmpty()) {
            recovered.put("tags", tags);
        }
        return recovered;
    }

    private String abbreviate(String text, int maxLength) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String extractJson(String text) {
        String trimmed = text == null ? "" : text.trim();
        int start = trimmed.indexOf('{');
        if (start < 0) {
            return trimmed;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = start; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return trimmed.substring(start, i + 1);
                }
            }
        }
        return trimmed;
    }

    private ExtractedText extractJsonStringField(String text, String fieldName, List<String> nextFields) {
        if (text == null || text.isBlank()) {
            return ExtractedText.incomplete("");
        }

        String direct = tryExtractBoundedJsonStringField(text, fieldName, nextFields);
        if (!direct.isBlank()) {
            return ExtractedText.complete(direct);
        }

        ExtractedText recovered = tryExtractTruncatedJsonStringField(text, fieldName, nextFields);
        if (!recovered.complete() && !recovered.value().isBlank()) {
            log.warn("discarded incomplete ai field {} during recovery", fieldName);
        }
        return recovered;
    }

    private String tryExtractBoundedJsonStringField(String text, String fieldName, List<String> nextFields) {
        String lookahead = nextFields.isEmpty()
                ? "\\}|$"
                : ",\\s*\"(?:" + String.join("|", nextFields) + ")\"|\\}";
        Pattern fieldPattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"(.*?)\"\\s*(?=" + lookahead + ")",
                Pattern.DOTALL
        );
        Matcher matcher = fieldPattern.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return unescapeJsonText(matcher.group(1)).trim();
    }

    private ExtractedText tryExtractTruncatedJsonStringField(String text, String fieldName, List<String> nextFields) {
        String marker = "\"" + fieldName + "\"";
        int markerIndex = text.indexOf(marker);
        if (markerIndex < 0) {
            return ExtractedText.incomplete("");
        }
        int colonIndex = text.indexOf(':', markerIndex + marker.length());
        if (colonIndex < 0) {
            return ExtractedText.incomplete("");
        }
        int openingQuoteIndex = text.indexOf('"', colonIndex + 1);
        if (openingQuoteIndex < 0) {
            return ExtractedText.incomplete("");
        }

        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int i = openingQuoteIndex + 1; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaping) {
                value.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                value.append(ch);
                escaping = true;
                continue;
            }
            if (ch == '"') {
                String tail = text.substring(i + 1);
                if (looksLikeFieldBoundary(tail, nextFields) || i == text.length() - 1) {
                    return ExtractedText.complete(unescapeJsonText(value.toString()).trim());
                }
            }
            value.append(ch);
        }

        return ExtractedText.incomplete(unescapeJsonText(value.toString()).trim());
    }

    private boolean looksLikeFieldBoundary(String tail, List<String> nextFields) {
        String trimmed = tail == null ? "" : tail.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("}") || trimmed.startsWith("]")) {
            return true;
        }
        if (!trimmed.startsWith(",")) {
            return false;
        }
        String afterComma = trimmed.substring(1).trim();
        if (afterComma.isEmpty() || afterComma.startsWith("\"") || afterComma.startsWith("}") || afterComma.startsWith("]")) {
            return true;
        }
        if (nextFields.isEmpty()) {
            return false;
        }
        for (String nextField : nextFields) {
            if (trimmed.matches(",\\s*\"" + Pattern.quote(nextField) + "\".*")) {
                return true;
            }
        }
        return false;
    }

    private Long extractRecommendedCategoryId(String text) {
        Matcher matcher = RECOMMENDED_CATEGORY_PATTERN.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return null;
        }
        String raw = matcher.group(1).replace("\"", "").trim();
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<String> extractTags(String text) {
        Matcher arrayMatcher = TAGS_ARRAY_PATTERN.matcher(text == null ? "" : text);
        if (!arrayMatcher.find()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        Matcher valueMatcher = QUOTED_VALUE_PATTERN.matcher(arrayMatcher.group(1));
        while (valueMatcher.find()) {
            values.add(unescapeJsonText(valueMatcher.group(1)));
        }
        return normalizeTags(values);
    }

    private Category resolveCategory(Object value, PostAssistRequest request, List<Category> categories,
                                     String title, String polishedContent) {
        Long id = toLong(value);
        Optional<Category> exact = categories.stream().filter(category -> category.getId().equals(id)).findFirst();
        if (exact.isPresent()) {
            return exact.get();
        }
        Long preferredId = request.getCategoryId();
        if (preferredId != null) {
            Optional<Category> preferred = categories.stream()
                    .filter(category -> category.getId().equals(preferredId))
                    .findFirst();
            if (preferred.isPresent()) {
                return preferred.get();
            }
        }
        Optional<Category> inferred = inferCategoryFromText(categories, title, polishedContent, request.getContent());
        if (inferred.isPresent()) {
            return inferred.get();
        }
        return categories.get(0);
    }

    private Optional<Category> inferCategoryFromText(List<Category> categories, String title, String polishedContent, String originalContent) {
        String text = (Objects.toString(title, "") + "\n" + Objects.toString(polishedContent, "") + "\n"
                + Objects.toString(originalContent, "")).toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return Optional.empty();
        }

        if (containsAny(text, "吐槽", "无语", "烦死", "崩溃", "离谱", "rant", "frustrat")) {
            Optional<Category> discussion = findCategoryByKeyword(categories, "Discussion", "\u5410\u69fd");
            if (discussion.isPresent()) return discussion;
        }

        if (containsAny(text, "谁知道", "请问", "求助", "有人知道", "想问", "怎么办", "?","\uff1f")) {
            Optional<Category> help = findCategoryByKeyword(categories, "Help", "\u6c42\u52a9");
            if (help.isPresent()) {
                return help;
            }
        }
        if (containsAny(text, "二手", "闲置", "出", "转", "出售", "收", "求购")) {
            Optional<Category> resale = findCategoryByKeyword(categories, "Marketplace", "\u95f2\u7f6e");
            if (resale.isPresent()) {
                return resale;
            }
        }
        if (containsAny(text, "投票", "选哪个", "大家选", "二选一")) {
            Optional<Category> vote = findCategoryByKeyword(categories, "Polls", "\u6295\u7968");
            if (vote.isPresent()) {
                return vote;
            }
        }
        if (containsAny(text, "放假", "课程", "上课", "食堂", "宿舍", "图书馆", "校园", "期末")) {
            Optional<Category> campusLife = findCategoryByKeyword(categories, "Daily Life", "\u65e5\u5e38\u751f\u6d3b");
            if (campusLife.isPresent()) {
                return campusLife;
            }
        }
        return Optional.empty();
    }

    private Optional<Category> findCategoryByKeyword(List<Category> categories, String... keywords) {
        return categories.stream()
                .filter(category -> Arrays.stream(keywords).anyMatch(keyword ->
                        Objects.toString(category.getName(), "").contains(keyword)))
                .findFirst();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<String> normalizeTags(Object raw) {
        Set<String> values = new LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                addTag(values, Objects.toString(item, ""));
            }
        } else if (raw != null) {
            String[] pieces = Objects.toString(raw, "").split("[,\uFF0C\u3001\\s]+");
            for (String piece : pieces) {
                addTag(values, piece);
            }
        }
        return new ArrayList<>(values).stream().limit(5).toList();
    }

    private List<String> ensureMinimumTags(List<String> tags, String categoryName, String title, String content) {
        LinkedHashSet<String> values = new LinkedHashSet<>(tags == null ? List.of() : tags);
        addTag(values, categoryName);

        String source = (Objects.toString(title, "") + " " + Objects.toString(content, "")).trim();
        for (String candidate : FALLBACK_TOPIC_TAGS) {
            if (values.size() >= 5) {
                break;
            }
            if (source.contains(candidate)) {
                addTag(values, candidate);
            }
        }
        for (String candidate : FALLBACK_GENERIC_TAGS) {
            if (values.size() >= 3) {
                break;
            }
            addTag(values, candidate);
        }
        return new ArrayList<>(values).stream().limit(5).toList();
    }

    private void addTag(Set<String> values, String raw) {
        String tag = raw == null ? "" : raw.replace("#", "").trim();
        if (!tag.isBlank() && tag.length() <= 12) {
            values.add(tag);
        }
    }

    private String normalizeText(Object value, int maxLength) {
        String text = Objects.toString(value, "").trim();
        if (text.length() > maxLength) {
            return text.substring(0, maxLength);
        }
        return text;
    }

    private String fallbackTitle(String rawContent) {
        String normalized = fallbackPolishedContent(rawContent);
        if (normalized.isBlank()) {
            return "\u6821\u56ed\u968f\u624b\u8bb0";
        }
        String firstSentence = normalized.split("[\\u3002\\uff01\\uff1f!?\\n\\r]", 2)[0].trim();
        String candidate = firstSentence.isBlank() ? normalized : firstSentence;
        if (candidate.length() > 20) {
            candidate = candidate.substring(0, 20).trim();
        }
        return candidate.isBlank() ? "\u6821\u56ed\u968f\u624b\u8bb0" : candidate;
    }

    private String fallbackPolishedContent(String rawContent) {
        return Objects.toString(rawContent, "")
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\\r?\\n{3,}", "\n\n")
                .trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : Objects.toString(fallback, "");
    }

    private String unescapeJsonText(String value) {
        return Objects.toString(value, "")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(Objects.toString(value).trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String readOpenAiError(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("error").path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
        }
        return body == null || body.isBlank() ? "HTTP error" : body;
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private record ExtractedText(String value, boolean complete) {
        private static ExtractedText complete(String value) {
            return new ExtractedText(value, true);
        }

        private static ExtractedText incomplete(String value) {
            return new ExtractedText(value, false);
        }
    }
}
