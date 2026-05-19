package com.campus.campus_backend.controller;

import com.campus.campus_backend.common.Result;
import com.campus.campus_backend.dto.PostAssistRequest;
import com.campus.campus_backend.service.GeminiPostAssistService;
import com.campus.campus_backend.vo.PostAssistResponseVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiPostAssistController {
    private final GeminiPostAssistService geminiPostAssistService;

    public AiPostAssistController(GeminiPostAssistService geminiPostAssistService) {
        this.geminiPostAssistService = geminiPostAssistService;
    }

    @PostMapping("/post-assist")
    public Result<PostAssistResponseVO> assistPost(@Validated @RequestBody PostAssistRequest request) {
        return Result.ok(geminiPostAssistService.assist(request));
    }
}
