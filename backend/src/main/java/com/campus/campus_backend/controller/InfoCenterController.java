package com.campus.campus_backend.controller;

import com.campus.campus_backend.common.BizException;
import com.campus.campus_backend.common.ErrorCode;
import com.campus.campus_backend.common.Result;
import com.campus.campus_backend.domain.User;
import com.campus.campus_backend.dto.info.CreateNoticeCommentRequest;
import com.campus.campus_backend.dto.info.UpdateSubscriptionKeywordsRequest;
import com.campus.campus_backend.repository.UserRepository;
import com.campus.campus_backend.service.info.InfoCenterService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class InfoCenterController {
    private final InfoCenterService infoCenterService;
    private final UserRepository userRepository;

    public InfoCenterController(InfoCenterService infoCenterService, UserRepository userRepository) {
        this.infoCenterService = infoCenterService;
        this.userRepository = userRepository;
    }

    @GetMapping("/info-sources")
    public Result<Object> listSources(@RequestParam(required = false) String keyword,
            @AuthenticationPrincipal UserDetails principal) {
        return Result.ok(infoCenterService.listSources(keyword, optionalUser(principal)));
    }

    @GetMapping("/info-sources/search")
    public Result<Object> searchSources(@RequestParam String keyword,
            @AuthenticationPrincipal UserDetails principal) {
        return Result.ok(infoCenterService.listSources(keyword, optionalUser(principal)));
    }

    @GetMapping("/info-subscriptions")
    public Result<Object> listSubscriptions(@AuthenticationPrincipal UserDetails principal) {
        return Result.ok(infoCenterService.listSubscriptions(requireUser(principal)));
    }

    @PostMapping("/info-subscriptions/{sourceId}")
    public Result<Object> subscribe(@PathVariable Long sourceId, @AuthenticationPrincipal UserDetails principal) {
        return Result.ok(infoCenterService.subscribe(sourceId, requireUser(principal)));
    }

    @DeleteMapping("/info-subscriptions/{sourceId}")
    public Result<Object> unsubscribe(@PathVariable Long sourceId, @AuthenticationPrincipal UserDetails principal) {
        return Result.ok(infoCenterService.unsubscribe(sourceId, requireUser(principal)));
    }

    @PutMapping("/info-subscriptions/{sourceId}/keywords")
    public Result<Object> updateKeywords(@PathVariable Long sourceId,
            @Valid @RequestBody UpdateSubscriptionKeywordsRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return Result.ok(infoCenterService.updateSubscriptionKeywords(sourceId, requireUser(principal), request));
    }

    @GetMapping("/info-center/notices")
    public Result<Object> listNotices(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean onlySubscribed,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal UserDetails principal) {
        return Result.ok(infoCenterService.listNotices(optionalUser(principal), category, sourceId, keyword, onlySubscribed,
                page, pageSize));
    }

    @GetMapping("/info-center/notices/categories")
    public Result<Object> listNoticeCategories(@AuthenticationPrincipal UserDetails principal) {
        return Result.ok(infoCenterService.listNoticeCategories(optionalUser(principal)));
    }

    @GetMapping("/info-center/notices/{id}")
    public Result<Object> noticeDetail(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return Result.ok(infoCenterService.getNoticeDetail(id, optionalUser(principal)));
    }

    @GetMapping("/info-center/notices/{id}/comments")
    public Result<Object> listComments(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return Result.ok(infoCenterService.listNoticeComments(id, optionalUser(principal)));
    }

    @PostMapping("/info-center/notices/{id}/comments")
    public Result<Object> createComment(@PathVariable Long id,
            @Valid @RequestBody CreateNoticeCommentRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return Result.ok(infoCenterService.createNoticeComment(id, requireUser(principal), request));
    }

    @DeleteMapping("/info-center/comments/{commentId}")
    public Result<Object> deleteComment(@PathVariable Long commentId, @AuthenticationPrincipal UserDetails principal) {
        infoCenterService.deleteNoticeComment(commentId, requireUser(principal));
        return Result.okMessage("删除成功");
    }

    @PostMapping("/info-center/comments/{commentId}/like")
    public Result<Object> likeComment(@PathVariable Long commentId, @AuthenticationPrincipal UserDetails principal) {
        return Result.ok(infoCenterService.likeNoticeComment(commentId, requireUser(principal)));
    }

    private User requireUser(UserDetails principal) {
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return optionalUser(principal);
    }

    private User optionalUser(UserDetails principal) {
        if (principal == null) {
            return null;
        }
        return userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }
}
