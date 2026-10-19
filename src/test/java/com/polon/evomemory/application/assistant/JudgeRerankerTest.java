package com.polon.evomemory.application.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.polon.evomemory.application.ports.LlmException;
import com.polon.evomemory.application.ports.LlmPort;
import com.polon.evomemory.application.ports.LlmRequest;
import com.polon.evomemory.config.LlmProperties;
import com.polon.evomemory.domain.memory.Memory;
import com.polon.evomemory.domain.memory.MemoryKind;

@ExtendWith(MockitoExtension.class)
class JudgeRerankerTest {

    @Mock
    private LlmPort llmPort;

    private JudgeReranker reranker() {
        LlmProperties properties = new LlmProperties();
        properties.setJudgeModel("judge-model");
        return new JudgeReranker(llmPort, properties);
    }

    private Memory memory(String content) {
        return new Memory(UUID.randomUUID(), "user", MemoryKind.INTEREST, "assunto", content, 0.9, null, java.time.Instant.now());
    }

    @Test
    void mapsJsonNumbersToCandidateIds() {
        Memory a = memory("A");
        Memory b = memory("B");
        Memory c = memory("C");
        java.util.concurrent.atomic.AtomicReference<LlmRequest> captured = new java.util.concurrent.atomic.AtomicReference<>();
        when(llmPort.generate(any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return "{\"relevant\": [1, 3]}";
        });

        Optional<List<UUID>> result = reranker().select("pergunta", List.of(a, b, c));

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(a.getId(), c.getId());
        assertThat(captured.get().model()).isEqualTo("judge-model");
        assertThat(captured.get().systemPrompt()).contains("conservador");
        assertThat(captured.get().userPrompt()).contains("1. [INTEREST] A");
    }

    @Test
    void emptySelectionIsAUsableDecision() {
        when(llmPort.generate(any())).thenReturn("{\"relevant\": []}");

        List<Memory> candidates = List.of(memory("A"));
        Optional<List<UUID>> result = reranker().select("pergunta", candidates);

        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void malformedAnswerDegeneratesToEmpty() {
        when(llmPort.generate(any())).thenReturn("sem JSON e sem números relevantes \ud83d\ude43");

        Optional<List<UUID>> result = reranker().select("pergunta", List.of(memory("A"), memory("B")));

        assertThat(result).isEmpty();
    }

    @Test
    void llmFailureDegeneratesToEmpty() {
        when(llmPort.generate(any())).thenThrow(new LlmException("indisponivel"));

        Optional<List<UUID>> result = reranker().select("pergunta", List.of(memory("A"), memory("B")));

        assertThat(result).isEmpty();
    }

    @Test
    void numberOutOfRangeSpoilsTheAnswer() {
        when(llmPort.generate(any())).thenReturn("{\"relevant\": [1, 99]}");

        Optional<List<UUID>> result = reranker().select("pergunta", List.of(memory("A"), memory("B")));

        assertThat(result).isEmpty();
    }

    @Test
    void recoversNumbersFromUnwrappedAnswers() {
        Memory a = memory("A");
        Memory b = memory("B");
        Memory c = memory("C");
        when(llmPort.generate(any())).thenReturn("relevant: 2 e 3");

        Optional<List<UUID>> result = reranker().select("pergunta", List.of(a, b, c));

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(b.getId(), c.getId());
    }

    @Test
    void emptyCandidateListNeverCallsTheLlm() {
        Optional<List<UUID>> result = reranker().select("pergunta", List.of());

        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(llmPort);
    }
}
