package com.campus.campus_backend.service;

import com.campus.campus_backend.vo.AiReviewResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiContentReviewServiceTest {
    private AiContentReviewService service;

    @BeforeEach
    void setUp() {
        service = new AiContentReviewService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "semanticCheckEnabled", false);
        ReflectionTestUtils.setField(service, "mediumThreshold", 1);
        service.initRules();
    }

    @Test
    void shouldReturnHighRiskForIllegalOrAbusiveText() {
        AiReviewResultVO result = service.review(
                "校园代写",
                "我可以代写论文，还可以办假证，联系我",
                List.of("兼职", "互助"));

        assertEquals("high", result.getRiskLevel());
        assertFalse(result.isCanUseDirectly());
        assertFalse(result.getHits().isEmpty());
    }

    @Test
    void shouldReturnLowRiskForNormalText() {
        AiReviewResultVO result = service.review(
                "求助",
                "想问一下图书馆晚上几点关门？谢谢大家。",
                List.of("校园生活", "求助"));

        assertEquals("low", result.getRiskLevel());
        assertTrue(result.isCanUseDirectly());
    }

    @Test
    void shouldReturnLowRiskWhenReviewDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);
        AiReviewResultVO result = service.review("测试", "任意内容", List.of());
        assertEquals("low", result.getRiskLevel());
        assertTrue(result.isCanUseDirectly());
    }
}
