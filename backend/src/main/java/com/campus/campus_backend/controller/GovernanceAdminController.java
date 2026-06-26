package com.campus.campus_backend.controller;

import com.campus.campus_backend.common.BizException;
import com.campus.campus_backend.common.ErrorCode;
import com.campus.campus_backend.common.Result;
import com.campus.campus_backend.domain.User;
import com.campus.campus_backend.dto.governance.GovernanceEventActionRequest;
import com.campus.campus_backend.dto.governance.GovernanceMergeRequest;
import com.campus.campus_backend.dto.governance.GovernanceMonitorRunRequest;
import com.campus.campus_backend.repository.UserRepository;
import com.campus.campus_backend.service.governance.GovernanceOrchestratorService;
import com.campus.campus_backend.service.governance.GovernanceViewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/governance")
public class GovernanceAdminController {
    private final GovernanceOrchestratorService governanceOrchestratorService;
    private final GovernanceViewService governanceViewService;
    private final UserRepository userRepository;

    public GovernanceAdminController(GovernanceOrchestratorService governanceOrchestratorService,
                                     GovernanceViewService governanceViewService,
                                     UserRepository userRepository) {
        this.governanceOrchestratorService = governanceOrchestratorService;
        this.governanceViewService = governanceViewService;
        this.userRepository = userRepository;
    }

    @PostMapping("/monitor/run")
    public Result<Object> runMonitor(@AuthenticationPrincipal UserDetails principal,
                                     @RequestBody(required = false) GovernanceMonitorRunRequest request) {
        ensureAdmin(principal);
        return Result.ok(governanceOrchestratorService.runMonitor(request == null ? new GovernanceMonitorRunRequest() : request));
    }

    @PostMapping("/monitor/start")
    public Result<Object> startMonitor(@AuthenticationPrincipal UserDetails principal,
                                       @RequestBody(required = false) GovernanceMonitorRunRequest request) {
        ensureAdmin(principal);
        return Result.ok(governanceOrchestratorService.startMonitor(request == null ? new GovernanceMonitorRunRequest() : request));
    }

    @GetMapping("/monitor/runs/{runId}")
    public Result<Object> monitorRun(@AuthenticationPrincipal UserDetails principal, @PathVariable String runId) {
        ensureAdmin(principal);
        return Result.ok(governanceOrchestratorService.getMonitorRun(runId));
    }

    @GetMapping("/events")
    public Result<Object> listEvents(@AuthenticationPrincipal UserDetails principal,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String riskLevel,
                                     @RequestParam(required = false) String rumorStatus,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int pageSize) {
        ensureAdmin(principal);
        return Result.ok(governanceViewService.listEvents(status, riskLevel, rumorStatus, page, pageSize));
    }

    @GetMapping("/events/{id}")
    public Result<Object> eventDetail(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        ensureAdmin(principal);
        return Result.ok(governanceViewService.getEventDetail(id));
    }

    @GetMapping("/events/{id}/snapshots")
    public Result<Object> snapshots(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        ensureAdmin(principal);
        return Result.ok(governanceViewService.listSnapshots(id));
    }

    @PostMapping("/events/{id}/reanalyze")
    public Result<Object> reanalyze(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        ensureAdmin(principal);
        return Result.ok(governanceOrchestratorService.reanalyzeEvent(id));
    }

    @PostMapping("/events/{id}/actions")
    public Result<Object> recordAction(@AuthenticationPrincipal UserDetails principal,
                                       @PathVariable Long id,
                                       @RequestBody GovernanceEventActionRequest request) {
        User admin = ensureAdmin(principal);
        return Result.ok(governanceOrchestratorService.recordAction(id, admin.getId(), request));
    }

    @PostMapping("/events/merge")
    public Result<Object> mergeEvents(@AuthenticationPrincipal UserDetails principal,
                                      @RequestBody GovernanceMergeRequest request) {
        User admin = ensureAdmin(principal);
        return Result.ok(governanceOrchestratorService.mergeEvents(request, admin.getId()));
    }

    @PostMapping("/events/{id}/close")
    public Result<Object> closeEvent(@AuthenticationPrincipal UserDetails principal,
                                     @PathVariable Long id,
                                     @RequestBody(required = false) Map<String, String> body) {
        User admin = ensureAdmin(principal);
        String note = body == null ? null : body.get("note");
        return Result.ok(governanceOrchestratorService.closeEvent(id, admin.getId(), note));
    }

    @GetMapping("/events/{id}/postmortem")
    public Result<Object> postmortem(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        ensureAdmin(principal);
        return Result.ok(governanceOrchestratorService.generatePostmortem(id));
    }

    @GetMapping("/official-data")
    public Result<Object> officialData(@AuthenticationPrincipal UserDetails principal,
                                       @RequestParam(required = false) Long eventId,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize) {
        ensureAdmin(principal);
        return Result.ok(governanceViewService.listOfficialEvidence(eventId, keyword, page, pageSize));
    }

    @PostMapping("/events/purge-all")
    public Result<Object> clearEvents(@AuthenticationPrincipal UserDetails principal) {
        ensureAdmin(principal);
        return Result.ok(governanceOrchestratorService.clearGovernanceData());
    }

    private User ensureAdmin(UserDetails principal) {
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        User user = userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
