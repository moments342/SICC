package com.moments.sicc.shared.exception;

public class ArmazenamentoException extends RuntimeException {
    public ArmazenamentoException(String message) {
        super(message);
    }

    public ArmazenamentoException(String message, Throwable cause) {
        super(message, cause);
    }
}
