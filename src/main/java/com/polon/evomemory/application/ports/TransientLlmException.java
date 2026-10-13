package com.polon.evomemory.application.ports;

public class TransientLlmException extends LlmException {

    public TransientLlmException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransientLlmException(String message) {
        super(message);
    }
}
