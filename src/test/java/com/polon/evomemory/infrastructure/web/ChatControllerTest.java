package com.polon.evomemory.infrastructure.web;
import com.polon.evomemory.application.model.response.MemoryView;
import com.polon.evomemory.application.model.response.ChatResponse;
import com.polon.evomemory.infrastructure.web.request.ChatRequest;
import com.polon.evomemory.domain.memory.MemoryKind;
import com.polon.evomemory.domain.memory.MemoryStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polon.evomemory.application.memory.ConversationService;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @MockBean
    private com.polon.evomemory.application.auth.AuthService authService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @MockBean
    private ConversationService conversationService;

    @Test
    void returnsMemoriesForValidRequest() throws Exception {
        when(authService.authenticate("Bearer test-token")).thenReturn("user");
        MemoryView view = new MemoryView(UUID.randomUUID(), MemoryKind.INTEREST, "sistemas-distribuidos",
                "Estuda sistemas distribuídos.", 0.7, MemoryStatus.ACTIVE, 1, Instant.now());
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of(view)));

        String body = mapper.writeValueAsString(new ChatRequest("conv", List.of("Estou estudando sistemas distribuídos.")));

        mockMvc.perform(post("/api/conversations/user")
                        .header("Authorization", "Bearer test-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memories[0].subjectKey").value("sistemas-distribuidos"))
                .andExpect(jsonPath("$.memories[0].kind").value("INTEREST"));
    }

    @Test
    void rejectsEmptyMessages() throws Exception {
        when(authService.authenticate("Bearer test-token")).thenReturn("user");
        String body = mapper.writeValueAsString(new ChatRequest("conv", List.of()));

        mockMvc.perform(post("/api/conversations/user")
                        .header("Authorization", "Bearer test-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
