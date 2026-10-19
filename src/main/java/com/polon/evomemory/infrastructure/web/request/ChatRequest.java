package com.polon.evomemory.infrastructure.web.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record ChatRequest(
        @NotBlank @Size(max = 100) String conversationId,
        @NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 10000) String> messages) {

    @AssertTrue(message = "Total message content exceeds the request budget")
    public boolean isWithinContentBudget() {
        return messages == null || messages.stream().filter(java.util.Objects::nonNull)
                .mapToLong(String::length).sum() <= 20000;
    }
}
