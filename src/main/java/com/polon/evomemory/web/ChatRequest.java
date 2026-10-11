package com.polon.evomemory.web;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record ChatRequest(
        @NotBlank String conversationId,
        @NotEmpty List<String> messages) {
}
