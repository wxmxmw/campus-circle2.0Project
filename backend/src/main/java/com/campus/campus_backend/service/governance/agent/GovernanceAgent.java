package com.campus.campus_backend.service.governance.agent;

public interface GovernanceAgent<I, O> {
    O execute(I input, GovernanceAgentContext context);
}
