package com.campus.campus_backend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiReviewResultVO {
    private String riskLevel;
    private List<String> categories;
    private List<AiReviewHitVO> hits;
    private String suggestion;
    private boolean canUseDirectly;
}
