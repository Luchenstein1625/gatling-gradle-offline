package cl.bci.performance.api.model;

import java.time.Instant;

public record ExecutionInfo(
        String executionId,
        String configurationId,
        ExecutionStatus status,
        Integer exitCode,
        String failureType,
        String message,
        Instant startedAt,
        Instant finishedAt
) {}
