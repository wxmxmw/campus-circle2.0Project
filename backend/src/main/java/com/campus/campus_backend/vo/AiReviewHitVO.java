package com.campus.campus_backend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiReviewHitVO {
    private String type;
    private String category;
    private String matchedText;
    private String severity;
}
