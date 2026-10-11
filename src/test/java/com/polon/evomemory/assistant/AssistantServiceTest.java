package com.polon.evomemory.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.polon.evomemory.config.LlmProperties;
import com.polon.evomemory.config.RetrievalProperties;
import com.polon.evomemory.llm.EmbeddingClient;
import com.polon.evomemory.llm.LlmClient;
import com.polon.evomemory.llm.LlmException;
import com.polon.evomemory.llm.LlmRequest;
import com.polon.evomemory.memory.ConversationService;
import com.polon.evomemory.memory.MemoryKind;
import com.polon.evomemory.memory.MemoryService;
import com.polon.evomemory.memory.MemoryStatus;
import com.polon.evomemory.memory.MemoryVectorStore;
import com.polon.evomemory.memory.SemanticMemory;
import com.polon.evomemory.web.ChatResponse;
import com.polon.evomemory.web.MemoryView;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    @Mock
    private MemoryService memoryService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private MemoryVectorStore vectorStore;

    private final AtomicReference<LlmRequest> captured = new AtomicReference<>();

    private AssistantService assistant(EmbeddingClient embeddings) {
        LlmProperties properties = new LlmProperties();
        properties.setChatModel("chat-model");
        LlmClient client = request -> {
            captured.set(request);
            return "resposta";
        };
        return new AssistantService(memoryService, conversationService, client, properties,
                embeddings, vectorStore, new RetrievalProperties(), FIXED_CLOCK);
    }

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void expiredPlanIsNotUsedAsContext() {
        SemanticMemory plan = memory(MemoryKind.PLAN, "viagem-japao", "Vai viajar ao Japão em março.");
        plan.setValidUntil(LocalDate.of(2026, 3, 31));
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(plan));

        assistant(embeddingsUnavailable()).chat("user", "conv", "Quero planejar uma viagem ao Japão.");

        assertThat(captured.get().userPrompt()).doesNotContain("Vai viajar ao Japão em março.");
    }

    @Test
    void planWithoutDateIsDroppedOnlyAfterNinetyDaysWithoutConfirmation() {
        SemanticMemory recent = memory(MemoryKind.PLAN, "curso", "Pretende fazer um curso de Rust.");
        SemanticMemory old = new SemanticMemory(UUID.randomUUID(), "user", MemoryKind.PLAN, "academia",
                "Pretende voltar para a academia.", 0.9, null, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(AssistantService.isStalePlan(recent, LocalDate.of(2026, 10, 10))).isFalse();
        assertThat(AssistantService.isStalePlan(old, LocalDate.of(2026, 10, 10))).isTrue();
    }

    @Test
    void healthMemoriesAreMarkedForCautiousUse() {
        SemanticMemory allergy = memory(MemoryKind.HEALTH, "alergia-amendoim", "É alérgico a amendoim.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(allergy));

        assistant(embeddingsUnavailable()).chat("user", "conv", "Qual receita de doce você sugere?");

        assertThat(captured.get().userPrompt()).contains("- [HEALTH] É alérgico a amendoim.");
        assertThat(captured.get().systemPrompt()).contains("[HEALTH]");
    }

    private static EmbeddingClient embeddingsUnavailable() {
        return text -> {
            throw new LlmException("embeddings indisponíveis");
        };
    }

    private static SemanticMemory memory(MemoryKind kind, String subjectKey, String content) {
        return new SemanticMemory(UUID.randomUUID(), "user", kind, subjectKey, content, 0.9, null, Instant.now());
    }

    @Test
    void includesRelevantMemoryAndLeavesOutUnrelatedOnes() {
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(
                memory(MemoryKind.INTEREST, "sistemas-distribuidos", "Está estudando sistemas distribuídos."),
                memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.")));

        assistant(embeddingsUnavailable()).chat("user", "conv", "Quero revisar consistência em sistemas distribuídos.");

        String prompt = captured.get().userPrompt();
        assertThat(prompt).contains("Está estudando sistemas distribuídos.");
        assertThat(prompt).doesNotContain("culinária");
        assertThat(captured.get().model()).isEqualTo("chat-model");
    }

    @Test
    void fallsBackToTopMemoriesWhenNothingMatches() {
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(
                memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.")));

        assistant(embeddingsUnavailable()).chat("user", "conv", "Oi, tudo bem?");

        assertThat(captured.get().userPrompt()).contains("Gosta de culinária italiana.");
    }

    @Test
    void includesSemanticMatchWithoutSharedWords() {
        SemanticMemory studying = memory(MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos.");
        SemanticMemory cooking = memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(studying, cooking));
        when(vectorStore.nearest(eq("user"), any(), anyInt()))
                .thenReturn(List.of(new MemoryVectorStore.Match(studying.getId(), 0.7)));

        assistant(text -> new float[] {0.1f, 0.2f}).chat("user", "conv", "Como faço replicação de dados?");

        assertThat(captured.get().userPrompt()).contains("Está estudando sistemas distribuídos.");
        assertThat(captured.get().userPrompt()).doesNotContain("culinária");
    }

    @Test
    void dropsWeakMatchesFarFromTheBestOne() {
        SemanticMemory replication = memory(MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos.");
        SemanticMemory react = memory(MemoryKind.PROJECT, "app-financas",
                "Está construindo um app de finanças pessoais em React.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(replication, react));
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of(
                new MemoryVectorStore.Match(replication.getId(), 0.70),
                new MemoryVectorStore.Match(react.getId(), 0.55)));

        assistant(text -> new float[] {0.1f}).chat("user", "conv", "Quero entender replicação de dados.");

        assertThat(captured.get().userPrompt()).contains("Está estudando sistemas distribuídos.");
        assertThat(captured.get().userPrompt()).doesNotContain("React");
    }

    @Test
    void ignoresSemanticMatchesBelowThreshold() {
        SemanticMemory studying = memory(MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos.");
        SemanticMemory cooking = memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(studying, cooking));
        when(vectorStore.nearest(eq("user"), any(), anyInt()))
                .thenReturn(List.of(new MemoryVectorStore.Match(cooking.getId(), 0.2)));

        assistant(text -> new float[] {0.1f}).chat("user", "conv", "Oi, tudo bem?");

        // Nada passou do limiar nem por palavras: cai no fallback, que inclui as memórias de maior confiança.
        assertThat(captured.get().userPrompt()).contains("Gosta de culinária italiana.");
        assertThat(captured.get().userPrompt()).contains("Está estudando sistemas distribuídos.");
    }

    @Test
    void keepsUpdatedMemoryInContextBecauseItHoldsOlderKnowledge() {
        // A mensagem foi absorvida por uma memória existente (versão 2): ela guarda o que se sabia
        // antes, então não pode ser descartada junto com a repetição da mensagem.
        SemanticMemory distributed = memory(MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos e quer entender replicação de dados.");
        MemoryView updated = new MemoryView(distributed.getId(), MemoryKind.INTEREST, "sistemas-distribuidos",
                distributed.getContent(), 0.9, MemoryStatus.ACTIVE, 2, Instant.now());
        SemanticMemory cooking = memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of(updated)));
        when(memoryService.recallActive("user")).thenReturn(List.of(distributed, cooking));
        when(vectorStore.nearest(eq("user"), any(), anyInt()))
                .thenReturn(List.of(new MemoryVectorStore.Match(distributed.getId(), 0.91)));

        assistant(text -> new float[] {0.1f}).chat("user", "conv", "Quero entender replicação de dados entre servidores.");

        assertThat(captured.get().userPrompt()).contains("sistemas distribuídos");
    }

    @Test
    void doesNotAdmitFailedMatchThroughSharedStopword() {
        SemanticMemory running = memory(MemoryKind.INTEREST, "corrida", "Gosta de corrida de longa distância.");
        // Compartilha apenas a preposição "para" com a pergunta, e reprova no limiar semântico.
        SemanticMemory trip = memory(MemoryKind.PLAN, "viagem-japao", "Vai viajar para o Japão em abril.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(running, trip));
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of(
                new MemoryVectorStore.Match(running.getId(), 0.61),
                new MemoryVectorStore.Match(trip.getId(), 0.50)));

        assistant(text -> new float[] {0.1f}).chat("user", "conv", "Vou começar a treinar para uma maratona.");

        assertThat(captured.get().userPrompt()).contains("Gosta de corrida de longa distância.");
        assertThat(captured.get().userPrompt()).doesNotContain("Japão");
    }

    @Test
    void ignoresSharedStopwordsWhenEmbeddingsAreUnavailable() {
        SemanticMemory studying = memory(MemoryKind.INTEREST, "sistemas-distribuidos", "Está estudando sistemas distribuídos.");
        SemanticMemory cooking = memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.");
        SemanticMemory cat = memory(MemoryKind.FACT, "gato", "Tem um gato chamado Miau.");
        // Compartilha só a preposição "para", e está fora do top 3 do fallback.
        SemanticMemory trip = memory(MemoryKind.PLAN, "viagem-japao", "Vai viajar para o Japão em abril.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(studying, cooking, cat, trip));

        assistant(embeddingsUnavailable()).chat("user", "conv", "Quero treinar para uma maratona.");

        assertThat(captured.get().userPrompt()).doesNotContain("Japão");
    }

    @Test
    void doesNotEchoMemoryJustLearnedFromTheMessage() {
        SemanticMemory echoed = memory(MemoryKind.INTEREST, "replicacao",
                "Quer entender replicação de dados.");
        SemanticMemory studying = memory(MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos.");
        MemoryView learnedNow = new MemoryView(echoed.getId(), MemoryKind.INTEREST, "replicacao",
                echoed.getContent(), 0.9, MemoryStatus.ACTIVE, 1, Instant.now());
        SemanticMemory cooking = memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.");
        SemanticMemory cat = memory(MemoryKind.FACT, "gato", "Tem um gato chamado Miau.");
        SemanticMemory coffee = memory(MemoryKind.PREFERENCE, "cafe", "Prefere café sem açúcar.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of(learnedNow)));
        // A memória de interesse fica por último: se a seleção caísse no fallback (top 3), ela ficaria de fora.
        when(memoryService.recallActive("user")).thenReturn(List.of(echoed, cooking, cat, coffee, studying));
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of(
                new MemoryVectorStore.Match(echoed.getId(), 0.99),
                new MemoryVectorStore.Match(studying.getId(), 0.62),
                new MemoryVectorStore.Match(cooking.getId(), 0.20)));

        assistant(text -> new float[] {0.1f}).chat("user", "conv", "Quer entender replicação de dados.");

        assertThat(captured.get().userPrompt()).contains("- [INTEREST] Está estudando sistemas distribuídos.");
        assertThat(captured.get().userPrompt()).doesNotContain("- [INTEREST] Quer entender replicação de dados.");
    }

    @Test
    void learnsFromMessageBeforeAnswering() {
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive(anyString())).thenReturn(List.of());

        assistant(embeddingsUnavailable()).chat("user", "conv", "Oi");

        verify(conversationService).process("user", "conv", List.of("Oi"));
    }

    @Test
    void emptyContextIsSignaledToTheModel() {
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of());

        assistant(embeddingsUnavailable()).chat("user", "conv", "Oi");

        assertThat(captured.get().userPrompt()).contains("nada registrado ainda");
    }
}
