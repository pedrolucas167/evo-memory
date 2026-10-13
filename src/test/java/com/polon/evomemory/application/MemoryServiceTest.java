package com.polon.evomemory.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.polon.evomemory.application.memory.MemoryService;
import com.polon.evomemory.application.ports.*;
import com.polon.evomemory.config.LlmProperties;
import com.polon.evomemory.domain.memory.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryServiceTest {
    private final MemoryStorePort store = mock(MemoryStorePort.class);
    private final ChangeClassifier classifier = mock(ChangeClassifier.class);
    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final VectorStorePort vectors = mock(VectorStorePort.class);
    private final Instant now = Instant.parse("2026-10-13T00:00:00Z");
    private final MemoryService service = new MemoryService(store, classifier,
            Clock.fixed(now, ZoneOffset.UTC), embeddings, vectors, new LlmProperties(), new MemoryEvolutionService());
    private final Episode episode = new Episode(UUID.randomUUID(), "user", "conv", "test", EpisodeSource.CHAT, now);

    private Memory memory() {
        return new Memory(UUID.randomUUID(), "user", MemoryKind.PLAN, "trip", "old", 0.9, episode.getId(), now);
    }

    @Test
    void firstObservationPersistsMemoryRevisionAndEmbedding() {
        when(store.findBySubject("user", "trip")).thenReturn(Optional.empty());
        var candidate = new CandidateMemory(MemoryKind.PLAN, "trip", "new", 0.7);
        var created = service.observe("user", episode, candidate);
        assertThat(created.getVersion()).isEqualTo(1);
        verify(store).saveMemory(created);
        verify(store).saveRevision(argThat(r -> r.getChangeType() == ChangeType.CREATED
                && r.getMemoryId().equals(created.getId()) && r.getPreviousContent() == null));
        verify(store).flush();
        verify(vectors).store(eq(created.getId()), any(), any());
    }

    @Test
    void contradictionReplacesContentAndClearsObsoleteDeadline() {
        var memory = memory();
        memory.setValidUntil(LocalDate.of(2027, 1, 1));
        var candidate = new CandidateMemory(MemoryKind.PLAN, "trip", "cancelled", 0.6);
        when(store.findBySubject("user", "trip")).thenReturn(Optional.of(memory));
        when(classifier.classify(memory, candidate)).thenReturn(
                new ChangeClassifier.Decision(ChangeType.CONTRADICTED, "cancelled", "updated"));
        service.observe("user", episode, candidate);
        assertThat(memory.getContent()).isEqualTo("cancelled");
        assertThat(memory.getConfidence()).isEqualTo(0.6);
        assertThat(memory.getValidUntil()).isNull();
        assertThat(memory.getVersion()).isEqualTo(2);
        verify(store).saveRevision(argThat(r -> "old".equals(r.getPreviousContent())
                && "cancelled".equals(r.getNewContent())));
    }

    @Test
    void complementPreservesTheStrongerConfidence() {
        var memory = memory();
        var candidate = new CandidateMemory(MemoryKind.PLAN, "trip", "detail", 0.4);
        when(store.findBySubject("user", "trip")).thenReturn(Optional.of(memory));
        when(classifier.classify(memory, candidate)).thenReturn(
                new ChangeClassifier.Decision(ChangeType.COMPLEMENTED, "old plus detail", "detail"));
        service.observe("user", episode, candidate);
        assertThat(memory.getConfidence()).isEqualTo(0.9);
        assertThat(memory.getContent()).isEqualTo("old plus detail");
    }

    @Test
    void forgottenMemoryIsNotReactivatedByObservation() {
        var memory = memory();
        memory.retract(now);
        when(store.findBySubject("user", "trip")).thenReturn(Optional.of(memory));
        assertThat(service.observe("user", episode,
                new CandidateMemory(MemoryKind.PLAN, "trip", "new", 0.7))).isSameAs(memory);
        verifyNoInteractions(classifier, embeddings, vectors);
        verify(store, never()).saveMemory(any());
    }

    @Test
    void correctionRequiresOwnership() {
        var id = UUID.randomUUID();
        when(store.findOwned("other", id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.correct("other", id, "new"))
                .isInstanceOf(java.util.NoSuchElementException.class);
        verify(store, never()).saveMemory(any());
    }
}
