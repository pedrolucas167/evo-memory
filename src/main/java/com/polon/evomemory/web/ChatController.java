package com.polon.evomemory.web;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.polon.evomemory.memory.ConversationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/conversations")
public class ChatController {

    private final ConversationService conversationService;

    public ChatController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/{userId}")
    public ChatResponse send(@NotBlank @PathVariable String userId,
                              @Valid @RequestBody ChatRequest request) {
        return conversationService.process(userId, request.conversationId(), request.messages());
    }
}
