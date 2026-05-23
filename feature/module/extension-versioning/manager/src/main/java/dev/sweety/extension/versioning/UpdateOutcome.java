package dev.sweety.extension.versioning;

public sealed interface UpdateOutcome {
    record UpToDate() implements UpdateOutcome {}
    record Updated() implements UpdateOutcome {}
    record CheckFailed(Throwable cause) implements UpdateOutcome {}

    static UpdateOutcome upToDate() { return new UpToDate(); }
    static UpdateOutcome updated() { return new Updated(); }
    static UpdateOutcome failed(Throwable cause) { return new CheckFailed(cause); }
}
