package com.polon.evomemory.infrastructure.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantRequest(
        @NotBlank @Size(max = 100) String conversationId,
        @NotBlank @Size(max = 10000) String message) {
}
