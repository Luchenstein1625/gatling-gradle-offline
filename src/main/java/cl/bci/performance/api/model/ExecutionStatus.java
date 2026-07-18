package cl.bci.performance.api.model;

public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    GENERATING_REPORT,
    SUCCESS,
    TEST_FAILED,
    PLATFORM_ERROR,
    CANCELLED
}
