package com.campus.campus_backend.dto.governance;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GovernanceEventActionRequest {
    private String actionType;
    private String actionNote;
    private Boolean fromAgent = false;
    private String officialTitle;
    private String officialContent;
}
