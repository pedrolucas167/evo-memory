package com.polon.evomemory.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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

    @Test
    void confirmationUsesOlsAttentionReadingFromVectorStore() {
        var memory = memory();
        var candidate = new CandidateMemory(MemoryKind.PLAN, "trip", "again", 0.7);
        when(store.findBySubject("user", "trip")).thenReturn(Optional.of(memory));
        when(store.findActiveByUser("user")).thenReturn(List.of(memory));
        when(embeddings.embed("again")).thenReturn(new float[]{1, 0});
        when(vectors.nearest(eq("user"), any(), eq(10))).thenReturn(List.of(new VectorStorePort.Match(memory.getId(), 0.9)));
        when(classifier.classify(memory, candidate)).thenReturn(
                new ChangeClassifier.Decision(ChangeType.CONFIRMED, "old", "same"));

        service.observe("user", episode, candidate);

        // atencao = 0,9 * 0,9 = 0,81 -> taxa 0,181 -> 0,9 + 0,181 * 0,1
        assertThat(memory.getConfidence()).isEqualTo(0.9181, within(1e-9));
        assertThat(memory.getEvidenceCount()).isEqualTo(2);
    }

    @Test
    void confirmationDegradesToLegacyGainWithoutVectorIndex() {
        var memory = memory();
        var candidate = new CandidateMemory(MemoryKind.PLAN, "trip", "again", 0.7);
        when(store.findBySubject("user", "trip")).thenReturn(Optional.of(memory));
        when(embeddings.embed("again")).thenReturn(new float[]{1, 0});
        when(vectors.nearest(isA(String.class), any(), anyInt()))
                .thenThrow(new IllegalStateException("vector store off"));
        when(classifier.classify(memory, candidate)).thenReturn(
                new ChangeClassifier.Decision(ChangeType.CONFIRMED, "old", "same"));

        service.observe("user", episode, candidate);

        // atencao 0: taxa legada de 0,1
        assertThat(memory.getConfidence()).isEqualTo(0.91, within(1e-9));
    }

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
    void aliasResolutionChainsContradictionAcrossEntityRename() {
        // Defeito do dossiê 2026-10-20: "Tobi" -> "Nina" criava linha paralela
        // sem cadeia de contradição. O alias agora encadeia na memória antiga.
        var old = new Memory(UUID.randomUUID(), "user", MemoryKind.FACT, "gato-tobi",
                "Adotou um gato chamado Tobi, que fugiu.", 0.8, episode.getId(), now);
        var candidate = new CandidateMemory(MemoryKind.FACT, "gata-nina",
                "Adotou uma gata chamada Nina para substituir Tobi.", 0.7);
        when(store.findBySubject("user", "gata-nina")).thenReturn(Optional.empty());
        when(store.findActiveByUser("user")).thenReturn(List.of(old));
        when(classifier.classify(old, candidate)).thenReturn(new ChangeClassifier.Decision(
                ChangeType.CONTRADICTED, "Adotou uma gata chamada Nina.", "troca de animal"));

        var result = service.observe("user", episode, candidate);

        assertThat(result).isSameAs(old);
        assertThat(old.getSubjectKey()).isEqualTo("gato-tobi");
        assertThat(old.getContent()).isEqualTo("Adotou uma gata chamada Nina.");
        assertThat(old.getConfidence()).isEqualTo(0.7, within(1e-9));
        assertThat(old.getVersion()).isEqualTo(2);
        verify(store).saveRevision(argThat(r -> r.getChangeType() == ChangeType.CONTRADICTED
                && "Adotou um gato chamado Tobi, que fugiu.".equals(r.getPreviousContent())));
    }

    @Test
    void aliasScanTriesNextCandidateAfterUnrelated() {
        var older = new Memory(UUID.randomUUID(), "user", MemoryKind.FACT, "gato-tobi",
                "Adotou um gato chamado Tobi, que fugiu.", 0.8, episode.getId(), now.minusSeconds(120));
        var newer = new Memory(UUID.randomUUID(), "user", MemoryKind.FACT, "animais",
                "Adotou animais: tobi foi o primeiro.", 0.6, episode.getId(), now);
        var candidate = new CandidateMemory(MemoryKind.FACT, "gata-nina",
                "Adotou uma gata chamada Nina para substituir Tobi.", 0.7);
        when(store.findBySubject("user", "gata-nina")).thenReturn(Optional.empty());
        when(store.findActiveByUser("user")).thenReturn(List.of(older, newer));
        when(classifier.classify(older, candidate)).thenReturn(new ChangeClassifier.Decision(
                ChangeType.UNRELATED, "", "assunto diferente"));
        when(classifier.classify(newer, candidate)).thenReturn(new ChangeClassifier.Decision(
                ChangeType.CONTRADICTED, "Adotou uma gata chamada Nina.", "troca de animal"));

        var result = service.observe("user", episode, candidate);

        assertThat(result).isSameAs(newer);
        assertThat(newer.getContent()).isEqualTo("Adotou uma gata chamada Nina.");
        assertThat(older.getContent()).isEqualTo("Adotou um gato chamado Tobi, que fugiu.");
        verify(classifier, times(2)).classify(any(), eq(candidate));
    }

    @Test
    void aliasRejectedByJudgeCreatesNewMemory() {
        var old = new Memory(UUID.randomUUID(), "user", MemoryKind.FACT, "academia",
                "Frequenta academia de musculação às segundas.", 0.8, episode.getId(), now);
        var candidate = new CandidateMemory(MemoryKind.FACT, "musculacao",
                "Musculação agora é na academia nova da rua.", 0.7);
        when(store.findBySubject("user", "musculacao")).thenReturn(Optional.empty());
        when(store.findActiveByUser("user")).thenReturn(List.of(old));
        when(classifier.classify(old, candidate)).thenReturn(new ChangeClassifier.Decision(
                ChangeType.UNRELATED, "", "assunto diferente"));

        var created = service.observe("user", episode, candidate);

        assertThat(created.getSubjectKey()).isEqualTo("musculacao");
        assertThat(created.getContent()).isEqualTo("Musculação agora é na academia nova da rua.");
        assertThat(created.getVersion()).isEqualTo(1);
        verify(store).saveMemory(created);
        assertThat(old.getContent()).isEqualTo("Frequenta academia de musculação às segundas.");
        verify(store, never()).saveMemory(same(old));
    }

    @Test
    void noAliasCandidateSkipsTheJudge() {
        var old = new Memory(UUID.randomUUID(), "user", MemoryKind.FACT, "viagem-janeiro",
                "Vai viajar com os amigos em janeiro.", 0.8, episode.getId(), now);
        var candidate = new CandidateMemory(MemoryKind.FACT, "curso-ingles",
                "Começou aulas de inglês na terça.", 0.7);
        when(store.findBySubject("user", "curso-ingles")).thenReturn(Optional.empty());
        when(store.findActiveByUser("user")).thenReturn(List.of(old));

        var created = service.observe("user", episode, candidate);

        assertThat(created.getSubjectKey()).isEqualTo("curso-ingles");
        verifyNoInteractions(classifier);
    }

    @Test
    void exactSubjectUnrelatedJudgePreservesMemory() {
        var memory = new Memory(UUID.randomUUID(), "user", MemoryKind.FACT, "viagem-janeiro",
                "Vai viajar com os amigos em janeiro.", 0.9, episode.getId(), now);
        var candidate = new CandidateMemory(MemoryKind.FACT, "viagem-janeiro", "Vai em janeiro.", 0.7);
        when(store.findBySubject("user", "viagem-janeiro")).thenReturn(Optional.of(memory));
        when(classifier.classify(memory, candidate)).thenReturn(new ChangeClassifier.Decision(
                ChangeType.UNRELATED, "", "sem relação temática"));

        service.observe("user", episode, candidate);

        assertThat(memory.getContent()).isEqualTo("Vai viajar com os amigos em janeiro.");
        assertThat(memory.getConfidence()).isEqualTo(0.9, within(1e-9));
        verify(store).saveRevision(argThat(r -> r.getChangeType() == ChangeType.COMPLEMENTED
                && "Vai viajar com os amigos em janeiro.".equals(r.getNewContent())));
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
