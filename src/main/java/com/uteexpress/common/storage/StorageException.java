package com.uteexpress.common.storage;

public final class StorageException extends RuntimeException {
    public enum Reason { EMPTY, TOO_LARGE, UNSUPPORTED_TYPE, INVALID_IMAGE, INVALID_PATH, IO_FAILURE }

    private final Reason reason;

    public StorageException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public StorageException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
