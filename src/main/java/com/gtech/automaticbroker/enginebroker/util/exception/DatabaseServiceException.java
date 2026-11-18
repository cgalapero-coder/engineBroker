package com.gtech.automaticbroker.enginebroker.util.exception;

public class DatabaseServiceException extends RuntimeException {

    public DatabaseServiceException(String message) {
        super(message);
    }

    public DatabaseServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
