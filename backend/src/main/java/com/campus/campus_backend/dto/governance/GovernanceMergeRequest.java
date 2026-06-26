package com.campus.campus_backend.dto.governance;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GovernanceMergeRequest {
    private Long sourceEventId;
    private Long targetEventId;
}
