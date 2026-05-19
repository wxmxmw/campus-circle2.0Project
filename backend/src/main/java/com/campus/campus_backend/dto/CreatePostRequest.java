package com.campus.campus_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreatePostRequest {
    @NotBlank(message = "content required")
    private String content;
    private String title;
    private List<String> tags;
    private List<String> images;
    private Boolean isAnonymous;
    private Map<String, Object> product;
    private VotePayload vote;
    private Long topicId;
    private String topicName;
    private Long categoryId;

    @Data
    public static class VotePayload {
        private List<String> options;
        private Boolean multiple;
    }
}
