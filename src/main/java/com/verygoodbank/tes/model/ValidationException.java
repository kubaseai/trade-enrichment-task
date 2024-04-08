package com.verygoodbank.tes.model;

public class ValidationException extends java.lang.Exception {
    String content;
    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, String content) {
        super(message);
        this.content = content;
    }

    public String getContent() {
        return content;
    }
}