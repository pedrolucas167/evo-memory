package com.polon.evomemory.web;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.polon.evomemory.assistant.AssistantService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/{userId}")
    public AssistantReply chat(@NotBlank @PathVariable String userId,
                               @Valid @RequestBody AssistantRequest request) {
        return assistantService.chat(userId, request.conversationId(), request.message());
    }
}
