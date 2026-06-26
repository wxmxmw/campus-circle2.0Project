package com.campus.campus_backend.service.governance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GovernanceTextSupport {
    private static final Pattern CHUNK_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z0-9]{2,18}");
    private static final Set<String> STOP_WORDS = Set.of(
            "我们", "你们", "他们", "这个", "那个", "已经", "因为", "所以", "就是", "但是", "如果",
            "还是", "然后", "一个", "一下", "一下子", "真的", "感觉", "今天", "现在", "这里", "学校",
            "同学", "大家", "事情", "问题", "怎么", "什么", "没有", "不是", "可以", "应该", "需要"
    );
    private static final List<String> NEGATIVE_WORDS = List.of(
            "投诉", "生气", "离谱", "崩溃", "愤怒", "不满", "失望", "混乱", "谣言", "造谣", "欺骗",
            "错误", "停电", "封校", "泄露", "事故", "危险", "举报", "维权", "刷屏", "争议"
    );
    private static final List<String> POSITIVE_WORDS = List.of(
            "感谢", "支持", "解决", "靠谱", "顺利", "喜欢", "赞", "帮助", "解释", "说明"
    );
    private static final List<String> DEMAND_MARKERS = List.of(
            "希望", "建议", "请", "麻烦", "求", "尽快", "什么时候", "能不能", "请问", "说明"
    );
    private static final List<String> RUMOR_MARKERS = List.of(
            "听说", "据说", "网传", "真的假的", "是真的吗", "未证实", "谣言", "爆料"
    );

    private final ObjectMapper objectMapper;

    public GovernanceTextSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String abbreviate(String text, int maxLength) {
        String value = normalizeText(text);
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim() + "...";
    }

    public String normalizeText(String text) {
        return Objects.toString(text, "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public List<String> extractKeywords(Collection<String> texts, int limit) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (String text : texts) {
            Matcher matcher = CHUNK_PATTERN.matcher(Objects.toString(text, ""));
            while (matcher.find()) {
                String token = matcher.group().trim();
                if (token.length() < 2 || token.length() > 18) {
                    continue;
                }
                String lower = token.toLowerCase(Locale.ROOT);
                if (STOP_WORDS.contains(lower)) {
                    continue;
                }
                counter.merge(token, 1, Integer::sum);
            }
        }
        return counter.entrySet().stream()
                .sorted((a, b) -> {
                    int countCompare = Integer.compare(b.getValue(), a.getValue());
                    if (countCompare != 0) {
                        return countCompare;
                    }
                    return Integer.compare(a.getKey().length(), b.getKey().length());
                })
                .map(Map.Entry::getKey)
                .distinct()
                .limit(limit)
                .toList();
    }

    public String detectSentiment(String text) {
        int negative = hitCount(text, NEGATIVE_WORDS);
        int positive = hitCount(text, POSITIVE_WORDS);
        if (negative > positive) {
            return "negative";
        }
        if (positive > negative) {
            return "positive";
        }
        return "neutral";
    }

    public boolean containsDemand(String text) {
        return hitCount(text, DEMAND_MARKERS) > 0;
    }

    public boolean containsRumorMarker(String text) {
        return hitCount(text, RUMOR_MARKERS) > 0;
    }

    public int hitCount(String text, List<String> lexicon) {
        String value = Objects.toString(text, "");
        int total = 0;
        for (String item : lexicon) {
            if (value.contains(item)) {
                total++;
            }
        }
        return total;
    }

    public BigDecimal ratio(int part, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    public Map<String, Object> readJsonObject(String rawJson) {
        try {
            if (rawJson == null || rawJson.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public List<String> readJsonStringList(String rawJson) {
        try {
            if (rawJson == null || rawJson.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(rawJson, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<String> deduplicatePreserveOrder(List<String> items, int limit) {
        Set<String> seen = new LinkedHashSet<>();
        for (String item : items) {
            String value = normalizeText(item);
            if (!value.isBlank()) {
                seen.add(value);
            }
            if (seen.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(seen);
    }
}
