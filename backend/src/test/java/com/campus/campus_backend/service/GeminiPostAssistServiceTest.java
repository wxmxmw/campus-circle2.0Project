package com.campus.campus_backend.service;

import com.campus.campus_backend.domain.Category;
import com.campus.campus_backend.dto.PostAssistRequest;
import com.campus.campus_backend.repository.CategoryRepository;
import com.campus.campus_backend.vo.AiReviewResultVO;
import com.campus.campus_backend.vo.PostAssistResponseVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiPostAssistServiceTest {
    private CategoryRepository categoryRepository;
    private AiContentReviewService aiContentReviewService;
    private GeminiPostAssistService service;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        aiContentReviewService = mock(AiContentReviewService.class);
        service = new GeminiPostAssistService(categoryRepository, new ObjectMapper(), aiContentReviewService);

        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "baseUrl", "https://example.com/v1");
        ReflectionTestUtils.setField(service, "requestTimeoutSeconds", 10);
        ReflectionTestUtils.setField(service, "connectTimeoutSeconds", 5);

        when(categoryRepository.findAll()).thenReturn(List.of(
                category(1L, "\u95f2\u7f6e"),
                category(2L, "\u6c42\u52a9"),
                category(3L, "\u65e5\u5e38\u751f\u6d3b"),
                category(4L, "\u6295\u7968"),
                category(5L, "\u5410\u69fd")
        ));
        when(aiContentReviewService.review(anyString(), anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new AiReviewResultVO("low", List.of("normal"), List.of(), "ok", true));
    }

    @Test
    void shouldRecoverWhenAiReturnsRawNewlineInsideJsonString() {
        GeminiPostAssistService spyService = org.mockito.Mockito.spy(service);
        String brokenJson = "{ \"title\": \"\u4ec0\u4e48\u65f6\u5019\u624d\u80fd\u7ed3\u675f\u671f\u672b\u554a\", "
                + "\"polishedContent\": \"\u671f\u672b\u5468\u5230\u5e95\u4ec0\u4e48\u65f6\u5019\u624d\u80fd\u7ed3\u675f\u554a\uff0c\u611f\u89c9\u6709\u8003\u4e0d\u5b8c\n"
                + "\u7684\u8bd5\u3002\u597d\u60f3\u51fa\u53bb\u73a9\u3002\", "
                + "\"recommendedCategoryId\": 2, "
                + "\"tags\": [\"\u671f\u672b\", \"\u8003\u8bd5\"] }";
        doReturn(brokenJson).when(spyService).callOpenAiCompatible(anyString());

        PostAssistRequest request = new PostAssistRequest();
        request.setContent("\u671f\u672b\u5468\u4ec0\u4e48\u65f6\u5019\u7ed3\u675f\uff0c\u8981\u8003\u591a\u5c11\u95e8\u554a\u5367\u69fd\u3002\u597d\u60f3\u51fa\u53bb\u73a9\u3002\u3002\u3002");

        PostAssistResponseVO result = spyService.assist(request);

        assertEquals("\u4ec0\u4e48\u65f6\u5019\u624d\u80fd\u7ed3\u675f\u671f\u672b\u554a", result.getTitle());
        assertTrue(result.getPolishedContent().contains("\n"));
        assertEquals(2L, result.getRecommendedCategoryId());
        assertTrue(result.getTags().size() >= 3);
        assertTrue(result.getTags().contains("\u671f\u672b"));
    }

    @Test
    void shouldFallbackToDraftWhenAiJsonIsIncomplete() {
        GeminiPostAssistService spyService = org.mockito.Mockito.spy(service);
        doReturn("{\"tags\":[\"\u671f\u672b\"]}").when(spyService).callOpenAiCompatible(anyString());

        PostAssistRequest request = new PostAssistRequest();
        request.setContent("\u671f\u672b\u5468\u4ec0\u4e48\u65f6\u5019\u7ed3\u675f\uff1f\u6211\u611f\u89c9\u5feb\u9876\u4e0d\u4f4f\u4e86\u3002");

        PostAssistResponseVO result = spyService.assist(request);

        assertFalse(result.getTitle().isBlank());
        assertEquals(request.getContent(), result.getPolishedContent());
        assertTrue(result.getTags().size() >= 3);
    }

    @Test
    void shouldRecoverPolishedContentAndInferHelpCategoryWhenResponseIsTruncated() {
        GeminiPostAssistService spyService = org.mockito.Mockito.spy(service);
        doReturn("{\"title\":\"\u6c42\u95ee\u5b66\u6821\u653e\u5047\u65f6\u95f4\",\"polishedContent\":\"\u8bf7\u95ee\u6709\u540c\u5b66\u77e5\u9053\u5b66\u6821\u5177\u4f53\u7684\u653e\u5047\u65e5\u671f\u5417\uff1f\u60f3\u63d0\u524d\u89c4\u5212\u4e00\u4e0b\uff0c\u8c22\u8c22\u5927\u5bb6\uff01\",\"recommended")
                .when(spyService).callOpenAiCompatible(anyString());

        PostAssistRequest request = new PostAssistRequest();
        request.setContent("\u8c01\u77e5\u9053\u4ec0\u4e48\u65f6\u5019\u653e\u5047\u5462");

        PostAssistResponseVO result = spyService.assist(request);

        assertEquals("\u6c42\u95ee\u5b66\u6821\u653e\u5047\u65f6\u95f4", result.getTitle());
        assertEquals("\u8bf7\u95ee\u6709\u540c\u5b66\u77e5\u9053\u5b66\u6821\u5177\u4f53\u7684\u653e\u5047\u65e5\u671f\u5417\uff1f\u60f3\u63d0\u524d\u89c4\u5212\u4e00\u4e0b\uff0c\u8c22\u8c22\u5927\u5bb6\uff01",
                result.getPolishedContent());
        assertEquals(2L, result.getRecommendedCategoryId());
        assertEquals("\u6c42\u52a9", result.getRecommendedCategoryName());
    }

    @Test
    void shouldFallbackToDraftWhenPolishedContentIsTruncatedMidString() {
        GeminiPostAssistService spyService = org.mockito.Mockito.spy(service);
        doReturn("{\"title\":\"\u56fe\u4e66\u9986\u5360\u5ea7\uff0c\u771f\u7684\u5fcd\u4e0d\u4e86\u4e86\",\"polishedContent\":\"\u5927\u5bb6\u597d\uff0c\u6700\u8fd1\u5728\u56fe\u4e66\u9986\u590d\u4e60\uff0c\u771f\u7684\u6709\u70b9\u65e0\u5948\u3002\\n\u65e9\u4e0a\u516b\u70b9\u534a\u5230\u9986")
                .when(spyService).callOpenAiCompatible(anyString());

        PostAssistRequest request = new PostAssistRequest();
        request.setContent("\u6700\u8fd1\u53bb\u56fe\u4e66\u9986\u771f\u7684\u5f88\u5d29\u6e83\uff0c\u65e9\u4e0a\u516b\u70b9\u534a\u8fdb\u53bb\u5df2\u7ecf\u6709\u4e00\u6392\u6392\u5ea7\u4f4d\u88ab\u4e66\u5305\u548c\u6c34\u676f\u5360\u7740\uff0c\u4eba\u5374\u4e0d\u89c1\u8e2a\u5f71\u3002\u6709\u4e9b\u4f4d\u7f6e\u751a\u81f3\u4e2d\u5348\u90fd\u6ca1\u4eba\u56de\u6765\u3002\u5927\u5bb6\u90fd\u8981\u590d\u4e60\uff0c\u80fd\u4e0d\u80fd\u522b\u628a\u516c\u5171\u8d44\u6e90\u5f53\u6210\u81ea\u5df1\u5bb6\u7684\uff1f");

        PostAssistResponseVO result = spyService.assist(request);

        assertEquals("\u56fe\u4e66\u9986\u5360\u5ea7\uff0c\u771f\u7684\u5fcd\u4e0d\u4e86\u4e86", result.getTitle());
        assertEquals(request.getContent(), result.getPolishedContent());
        assertEquals(5L, result.getRecommendedCategoryId());
        assertEquals("\u5410\u69fd", result.getRecommendedCategoryName());
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setIsActive(true);
        return category;
    }
}
