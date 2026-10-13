package com.polon.evomemory.infrastructure.web;
import com.polon.evomemory.application.model.ReindexResult;
import com.polon.evomemory.application.model.ChatResponse;
import com.polon.evomemory.application.model.AssistantReply;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.polon.evomemory.application.auth.AuthService;
import com.polon.evomemory.application.assistant.AssistantService;
import com.polon.evomemory.application.memory.ConversationService;
import com.polon.evomemory.application.memory.MemoryService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AssistantController.class, ChatController.class, MemoryController.class, ExperimentController.class})
class ApiAuthorizationTest {
    @Autowired private MockMvc mvc;
    @MockBean private AuthService auth;
    @MockBean private AssistantService assistant;
    @MockBean private ConversationService conversations;
    @MockBean private MemoryService memories;
    @MockBean private com.polon.evomemory.config.LlmProperties llm;
    @MockBean private com.polon.evomemory.config.RetrievalProperties retrieval;
    private final UUID memoryId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private List<MockHttpServletRequestBuilder> requests() {
        return List.of(
                request(HttpMethod.POST, "/api/assistant/owner"),
                request(HttpMethod.POST, "/api/conversations/owner"),
                request(HttpMethod.POST, "/api/memories/owner/reindex"),
                request(HttpMethod.GET, "/api/memories/owner/graph"),
                request(HttpMethod.PATCH, "/api/memories/owner/" + memoryId),
                request(HttpMethod.DELETE, "/api/memories/owner/" + memoryId),
                request(HttpMethod.GET, "/api/memories/owner/" + memoryId + "/history"),
                request(HttpMethod.GET, "/api/experiments/owner/configuration"));
    }

    @Test
    void allRoutesRejectMissingCredentialsBeforeCallingServices() throws Exception {
        for (var request : requests()) mvc.perform(request).andExpect(status().isUnauthorized());
        verifyNoInteractions(assistant, conversations, memories);
    }

    @Test
    void allRoutesRejectInvalidCredentialsBeforeCallingServices() throws Exception {
        for (var request : requests()) mvc.perform(request.header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(assistant, conversations, memories);
    }

    @Test
    void allRoutesRejectAnotherUsersIdentity() throws Exception {
        when(auth.authenticate("Bearer other")).thenReturn("other");
        for (var request : requests()) mvc.perform(request.header("Authorization", "Bearer other"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(assistant, conversations, memories);
    }

    @Test
    void allRoutesAcceptMatchingVerifiedIdentity() throws Exception {
        when(auth.authenticate("Bearer owner")).thenReturn("owner");
        when(assistant.chat("owner", "conv", "message"))
                .thenReturn(new AssistantReply("answer", List.of(), List.of()));
        when(conversations.process(eq("owner"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memories.recallActive("owner")).thenReturn(List.of());
        when(memories.reindex("owner")).thenReturn(new ReindexResult(0, 0));
        var memory = new com.polon.evomemory.domain.memory.Memory(memoryId, "owner",
                com.polon.evomemory.domain.memory.MemoryKind.FACT, "fact", "message", 0.9, null,
                java.time.Instant.now());
        when(memories.correct("owner", memoryId, "message")).thenReturn(memory);
        when(memories.forget("owner", memoryId)).thenReturn(memory);
        when(memories.history("owner", memoryId)).thenReturn(List.of());
        when(llm.getChatModel()).thenReturn("chat");
        when(llm.getExtractionModel()).thenReturn("extract");
        when(llm.getJudgeModel()).thenReturn("judge");
        when(llm.getEmbeddingModel()).thenReturn("embed");
        for (var request : requests()) mvc.perform(request.header("Authorization", "Bearer owner")
                .contentType("application/json")
                .content("{\"conversationId\":\"conv\",\"message\":\"message\",\"messages\":[\"message\"],\"content\":\"message\"}"))
                .andExpect(status().isOk());
    }
}
