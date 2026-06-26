package com.campus.campus_backend.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GovernanceAiSupportService {
    private static final Logger log = LoggerFactory.getLogger(GovernanceAiSupportService.class);

    private final ObjectMapper objectMapper;

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

    public GovernanceAiSupportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Map<String, Object> completeJson(String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            return Map.of();
        }
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.2);
            requestBody.put("max_tokens", 1500);
            requestBody.put("response_format", Map.of("type", "json_object"));
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));

            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.max(connectTimeoutSeconds, 1)))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(requestTimeoutSeconds, 1)))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("governance ai request failed: status={}, body={}", response.statusCode(), abbreviate(response.body()));
                return Map.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                return Map.of();
            }
            String json = extractJson(content);
            JsonNode parsed = objectMapper.readTree(json);
            if (!parsed.isObject()) {
                return Map.of();
            }
            return objectMapper.convertValue(parsed, objectMapper.getTypeFactory()
                    .constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (Exception ex) {
            log.warn("governance ai degraded: {}", ex.getMessage());
            return Map.of();
        }
    }

    private String extractJson(String text) {
        String raw = stripMarkdownFence(text);
        int start = raw.indexOf('{');
        if (start < 0) {
            return raw;
        }
        int end = findBalancedJsonEnd(raw, start);
        if (end > start) {
            return raw.substring(start, end + 1);
        }
        return closeOpenJson(raw.substring(start));
    }

    private String stripMarkdownFence(String text) {
        String raw = text == null ? "" : text.trim();
        if (raw.startsWith("```")) {
            int firstLineBreak = raw.indexOf('\n');
            if (firstLineBreak >= 0) {
                raw = raw.substring(firstLineBreak + 1);
            }
            int closingFence = raw.lastIndexOf("```");
            if (closingFence >= 0) {
                raw = raw.substring(0, closingFence);
            }
        }
        return raw.trim();
    }

    private int findBalancedJsonEnd(String text, int start) {
        boolean inString = false;
        boolean escaping = false;
        int braceDepth = 0;
        int bracketDepth = 0;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                    continue;
                }
                if (ch == '\\') {
                    escaping = true;
                    continue;
                }
                if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == '{') {
                braceDepth++;
            } else if (ch == '}') {
                braceDepth--;
                if (braceDepth == 0 && bracketDepth == 0) {
                    return i;
                }
            } else if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']') {
                bracketDepth = Math.max(0, bracketDepth - 1);
            }
        }
        return -1;
    }

    private String closeOpenJson(String text) {
        StringBuilder builder = new StringBuilder(text == null ? "" : text.trim());
        boolean inString = false;
        boolean escaping = false;
        int braceDepth = 0;
        int bracketDepth = 0;
        for (int i = 0; i < builder.length(); i++) {
            char ch = builder.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                    continue;
                }
                if (ch == '\\') {
                    escaping = true;
                    continue;
                }
                if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                braceDepth++;
            } else if (ch == '}') {
                braceDepth = Math.max(0, braceDepth - 1);
            } else if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']') {
                bracketDepth = Math.max(0, bracketDepth - 1);
            }
        }
        if (escaping) {
            builder.append('"');
            inString = false;
        } else if (inString) {
            builder.append('"');
        }
        while (bracketDepth-- > 0) {
            builder.append(']');
        }
        while (braceDepth-- > 0) {
            builder.append('}');
        }
        return builder.toString();
    }

    private String trimTrailingSlash(String value) {
        String result = value == null || value.isBlank() ? "https://api.openai.com/v1" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 240 ? normalized.substring(0, 240) + "..." : normalized;
    }
}
