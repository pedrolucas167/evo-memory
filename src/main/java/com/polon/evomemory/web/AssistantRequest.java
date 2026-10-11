package com.polon.evomemory.web;

import jakarta.validation.constraints.NotBlank;

public record AssistantRequest(
        @NotBlank String conversationId,
        @NotBlank String message) {
}
