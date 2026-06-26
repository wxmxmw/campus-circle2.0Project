package com.campus.campus_backend.dto.governance;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GovernanceMonitorRunRequest {
    private Integer windowHours = 24;
    private Boolean includePosts = true;
    private Boolean includeComments = true;
    private Boolean includeReports = true;
    private Integer maxSignals = 120;
}
