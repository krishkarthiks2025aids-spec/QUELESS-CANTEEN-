package com.queueless.canteen.service;

/** A problem the caller can fix (bad input, not found, wrong state). status is the HTTP status code. */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
