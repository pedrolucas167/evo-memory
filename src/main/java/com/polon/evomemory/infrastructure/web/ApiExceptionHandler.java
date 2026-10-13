package com.polon.evomemory.infrastructure.web;

import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(404).body(Map.of("error", "Resource not found"));
    }

    @ExceptionHandler(com.polon.evomemory.application.ports.LlmException.class)
    public ResponseEntity<Map<String, String>> upstreamFailure() {
        return ResponseEntity.status(502).body(Map.of("error", "Model provider unavailable"));
    }
}
