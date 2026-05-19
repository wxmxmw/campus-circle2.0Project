package com.campus.campus_backend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostAssistResponseVO {
    private String title;
    private String polishedContent;
    private Long recommendedCategoryId;
    private String recommendedCategoryName;
    private List<String> tags;
    private AiReviewResultVO review;
}
