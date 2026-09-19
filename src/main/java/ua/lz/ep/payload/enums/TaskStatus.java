package ua.lz.ep.payload.enums;

public enum TaskStatus {
    PENDING,
    RUNNING,
    CANCELLED,
    TIMED_OUT,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == CANCELLED || this == TIMED_OUT || this == COMPLETED || this == FAILED;
    }
}
