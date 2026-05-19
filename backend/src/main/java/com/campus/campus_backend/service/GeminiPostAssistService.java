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
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class GeminiPostAssistService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GeminiPostAssistService.class);

    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;
    private final AiContentReviewService aiContentReviewService;

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-2.5-flash}")
    private String model;

    @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String baseUrl;

    @Value("${ai.gemini.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${ai.gemini.request-timeout-seconds:30}")
    private int requestTimeoutSeconds;

    @Value("${ai.gemini.proxy-host:}")
    private String proxyHost;

    @Value("${ai.gemini.proxy-port:0}")
    private int proxyPort;

    public GeminiPostAssistService(CategoryRepository categoryRepository, ObjectMapper objectMapper,
                                   AiContentReviewService aiContentReviewService) {
        this.categoryRepository = categoryRepository;
        this.objectMapper = objectMapper;
        this.aiContentReviewService = aiContentReviewService;
    }

    public PostAssistResponseVO assist(PostAssistRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "Please configure GEMINI_API_KEY before using AI post assistant");
        }

        List<Category> categories = categoryRepository.findAll().stream()
                .filter(category -> !Boolean.FALSE.equals(category.getIsActive()))
                .toList();
        if (categories.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "No category is configured for topic recommendation");
        }

        String prompt = buildPrompt(request, categories);
        String rawText = callGemini(prompt);
        Map<String, Object> ai = parseJsonObject(rawText);

        String title = normalizeText(ai.get("title"), 40);
        String polishedContent = normalizeText(ai.get("polishedContent"), 2000);
        if (title.isBlank() || polishedContent.isBlank()) {
            throw new BizException(ErrorCode.SERVER_ERROR.getCode(), "Gemini returned incomplete content");
        }

        Category recommended = resolveCategory(ai.get("recommendedCategoryId"), request.getCategoryId(), categories);
        List<String> tags = normalizeTags(ai.get("tags"));
        if (tags.size() < 3) {
            throw new BizException(ErrorCode.SERVER_ERROR.getCode(), "Gemini returned fewer than 3 tags");
        }

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
                - Return JSON only. Do not use Markdown.
                - JSON shape:
                  {"title":"...","polishedContent":"...","recommendedCategoryId":1,"tags":["...","...","..."]}
                - recommendedCategoryId must be one of the provided category ids.
                - title must be Chinese, natural, and no longer than 20 Chinese characters when possible.
                - polishedContent should preserve the user's meaning and not invent facts.
                - tags should be short Chinese keywords without "#".

                Existing categories:
                %s

                User selected category id:
                %s

                User draft:
                %s
                """.formatted(toJson(categoryPayload), Objects.toString(request.getCategoryId(), "null"),
                request.getContent());
    }

    private String callGemini(String prompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contents", List.of(Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", prompt)))));
            body.put("generationConfig", Map.of(
                    "temperature", 0.4,
                    "maxOutputTokens", 1200,
                    "responseMimeType", "application/json"));

            String endpoint = trimTrailingSlash(baseUrl) + "/" + normalizeModel(model) + ":generateContent";
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpClient httpClient = buildHttpClient();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(ErrorCode.SERVER_ERROR.getCode(), "Gemini request failed: " + readGeminiError(response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new BizException(ErrorCode.SERVER_ERROR.getCode(), "Gemini did not return usable content");
            }
            String text = parts.get(0).path("text").asText("");
            if (text.isBlank()) {
                throw new BizException(ErrorCode.SERVER_ERROR.getCode(), "Gemini returned empty content");
            }
            return text;
        } catch (BizException ex) {
            throw ex;
        } catch (HttpConnectTimeoutException ex) {
            throw new BizException(ErrorCode.SERVER_ERROR.getCode(),
                    "Cannot connect to Gemini API. Check local network access to Google or configure ai.gemini.proxy-host/port.");
        } catch (ConnectException ex) {
            throw new BizException(ErrorCode.SERVER_ERROR.getCode(),
                    "Connection to Gemini API was refused or blocked. Check network or configure ai.gemini.proxy-host/port.");
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SERVER_ERROR.getCode(), "Gemini request error: " + ex.getMessage());
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
        String json = extractJson(text);
        try {
            Object value = objectMapper.readValue(json, Object.class);
            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SERVER_ERROR.getCode(), "Failed to parse Gemini JSON response");
        }
        throw new BizException(ErrorCode.SERVER_ERROR.getCode(), "Gemini response is not a JSON object");
    }

    private Category resolveCategory(Object value, Long preferredId, List<Category> categories) {
        Long id = toLong(value);
        Optional<Category> exact = categories.stream().filter(category -> category.getId().equals(id)).findFirst();
        if (exact.isPresent()) {
            return exact.get();
        }
        if (preferredId != null) {
            Optional<Category> preferred = categories.stream()
                    .filter(category -> category.getId().equals(preferredId))
                    .findFirst();
            if (preferred.isPresent()) {
                return preferred.get();
            }
        }
        return categories.get(0);
    }

    private List<String> normalizeTags(Object raw) {
        Set<String> values = new LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                addTag(values, Objects.toString(item, ""));
            }
        } else if (raw != null) {
            String[] pieces = Objects.toString(raw, "").split("[,，、\\s]+");
            for (String piece : pieces) {
                addTag(values, piece);
            }
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

    private String extractJson(String text) {
        String trimmed = text == null ? "" : text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String readGeminiError(String body) {
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
        String modelName = value == null || value.isBlank() ? "gemini-2.5-flash" : value.trim();
        if (modelName.toLowerCase(Locale.ROOT).startsWith("models/")) {
            return modelName.substring("models/".length());
        }
        return modelName;
    }

    private String trimTrailingSlash(String value) {
        String result = value == null || value.isBlank()
                ? "https://generativelanguage.googleapis.com/v1beta/models"
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
}
