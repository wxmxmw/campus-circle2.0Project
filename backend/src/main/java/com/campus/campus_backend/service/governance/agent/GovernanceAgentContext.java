package com.campus.campus_backend.service.governance.agent;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class GovernanceAgentContext {
    private Long eventId;
    private String triggerSource;
    private Integer snapshotVersion;
    private Integer windowHours;
    private boolean aiEnabled = true;
    private final Map<String, Object> trace = new LinkedHashMap<>();
    private final LocalDateTime startedAt = LocalDateTime.now();

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getTriggerSource() {
        return triggerSource;
    }

    public void setTriggerSource(String triggerSource) {
        this.triggerSource = triggerSource;
    }

    public Integer getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshotVersion(Integer snapshotVersion) {
        this.snapshotVersion = snapshotVersion;
    }

    public Integer getWindowHours() {
        return windowHours;
    }

    public void setWindowHours(Integer windowHours) {
        this.windowHours = windowHours;
    }

    public boolean isAiEnabled() {
        return aiEnabled;
    }

    public void setAiEnabled(boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }

    public Map<String, Object> getTrace() {
        return trace;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void putTrace(String key, Object value) {
        trace.put(key, value);
    }
}
