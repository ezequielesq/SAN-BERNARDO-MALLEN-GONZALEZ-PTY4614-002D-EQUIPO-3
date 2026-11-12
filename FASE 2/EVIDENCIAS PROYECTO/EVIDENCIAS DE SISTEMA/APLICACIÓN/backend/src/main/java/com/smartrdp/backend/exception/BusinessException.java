package com.smartrdp.backend.exception;

public class BusinessException extends RuntimeException {

    private final String campo;

    public BusinessException(String message) {
        this(message, null);
    }

    public BusinessException(String message, String campo) {
        super(message);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
