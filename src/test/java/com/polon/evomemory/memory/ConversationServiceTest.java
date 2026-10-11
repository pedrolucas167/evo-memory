package com.polon.evomemory.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private MemoryService memoryService;

    @Mock
    private ExtractionService extractionService;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    void recordsEpisodeAndObservesEachCandidate() {
        Episode episode = new Episode(UUID.randomUUID(), "user", "conv", "resumo", EpisodeSource.CHAT,
                java.time.Instant.now());
        SemanticMemory saved = new SemanticMemory(UUID.randomUUID(), "user", MemoryKind.INTEREST,
                "sistemas-distribuidos", "Estuda sistemas distribuídos.", 0.7, episode.getId(),
                java.time.Instant.now());
        when(memoryService.recordEpisode("user", "conv", "Estudo de sistemas distribuídos.", EpisodeSource.CHAT))
                .thenReturn(episode);
        when(memoryService.recallActive("user")).thenReturn(List.of());
        when(extractionService.extract(any(), any())).thenReturn(List.of(
                new CandidateMemory(MemoryKind.INTEREST, "sistemas-distribuidos", "Estuda sistemas distribuídos.", 0.7)));
        when(memoryService.observe("user", episode,
                new CandidateMemory(MemoryKind.INTEREST, "sistemas-distribuidos", "Estuda sistemas distribuídos.", 0.7)))
                .thenReturn(saved);

        ChatResponseIgnored response = run("Estudo de sistemas distribuídos.");

        verify(memoryService).recordEpisode("user", "conv", "Estudo de sistemas distribuídos.", EpisodeSource.CHAT);
        verify(memoryService).observe("user", episode,
                new CandidateMemory(MemoryKind.INTEREST, "sistemas-distribuidos", "Estuda sistemas distribuídos.", 0.7));
        assertThat(response).isNotNull();
    }

    private ChatResponseIgnored run(String message) {
        // Conversa com uma única mensagem: o resumo é a própria mensagem.
        return new ChatResponseIgnored(conversationService.process("user", "conv", List.of(message)));
    }

    private record ChatResponseIgnored(Object delegate) {
    }
}
