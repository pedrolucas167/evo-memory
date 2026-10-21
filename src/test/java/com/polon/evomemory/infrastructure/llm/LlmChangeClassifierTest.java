package com.polon.evomemory.infrastructure.llm;
import com.polon.evomemory.application.ports.ChangeClassifier;
import com.polon.evomemory.domain.memory.Memory;
import com.polon.evomemory.domain.memory.MemoryKind;
import com.polon.evomemory.domain.memory.CandidateMemory;
import com.polon.evomemory.domain.memory.ChangeType;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polon.evomemory.config.LlmProperties;
import com.polon.evomemory.application.ports.LlmClient;
import com.polon.evomemory.application.ports.LlmException;

class LlmChangeClassifierTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private final Memory current = new Memory(
            UUID.randomUUID(), "user", MemoryKind.FACT, "cidade-residencia",
            "Mora em Curitiba.", 0.9, null, Instant.now());

    private final CandidateMemory candidate = new CandidateMemory(
            MemoryKind.FACT, "cidade-residencia", "Mora em Florianópolis.", 0.9);

    private LlmChangeClassifier classifierReturning(String response) {
        LlmProperties properties = new LlmProperties();
        properties.setJudgeModel("judge-model");
        LlmClient client = request -> response;
        return new LlmChangeClassifier(client, properties, mapper, new SimpleChangeClassifier());
    }

    private LlmChangeClassifier classifierFailing() {
        LlmProperties properties = new LlmProperties();
        properties.setJudgeModel("judge-model");
        LlmClient client = request -> {
            throw new LlmException("sem rede");
        };
        return new LlmChangeClassifier(client, properties, mapper, new SimpleChangeClassifier());
    }

    @Test
    void contradictionUsesJudgeContent() {
        ChangeClassifier.Decision decision = classifierReturning("""
                {"decision": "CONTRADICTED", "content": "Mora em Florianópolis.", "reason": "mudou de cidade"}""")
                .classify(current, candidate);

        assertThat(decision.type()).isEqualTo(ChangeType.CONTRADICTED);
        assertThat(decision.content()).isEqualTo("Mora em Florianópolis.");
        assertThat(decision.reason()).contains("mudou de cidade");
    }

    @Test
    void complementUsesMergedContent() {
        ChangeClassifier.Decision decision = classifierReturning("""
                {"decision": "COMPLEMENTED", "content": "Mora em Curitiba e trabalha em Florianópolis.", "reason": "detalhe novo"}""")
                .classify(current, candidate);

        assertThat(decision.type()).isEqualTo(ChangeType.COMPLEMENTED);
        assertThat(decision.content()).isEqualTo("Mora em Curitiba e trabalha em Florianópolis.");
    }

    @Test
    void confirmationKeepsCurrentContent() {
        ChangeClassifier.Decision decision = classifierReturning("""
                {"decision": "CONFIRMED", "content": "outra coisa", "reason": "repetição"}""")
                .classify(current, candidate);

        assertThat(decision.type()).isEqualTo(ChangeType.CONFIRMED);
        assertThat(decision.content()).isEqualTo("Mora em Curitiba.");
    }

    @Test
    void acceptsFencedJson() {
        ChangeClassifier.Decision decision = classifierReturning("""
                ```json
                {"decision": "CONTRADICTED", "content": "Mora em Florianópolis.", "reason": "x"}
                ```""")
                .classify(current, candidate);

        assertThat(decision.type()).isEqualTo(ChangeType.CONTRADICTED);
    }

    @Test
    void fallsBackToTextComparisonOnInvalidJson() {
        ChangeClassifier.Decision decision = classifierReturning("não sei responder")
                .classify(current, candidate);

        assertThat(decision.type()).isEqualTo(ChangeType.CONTRADICTED);
        assertThat(decision.content()).isEqualTo("Mora em Florianópolis.");
    }

    @Test
    void unrelatedCarriesNoContent() {
        ChangeClassifier.Decision decision = classifierReturning("""
                {"decision": "UNRELATED", "content": "", "reason": "assunto diferente"}""")
                .classify(current, candidate);

        assertThat(decision.type()).isEqualTo(ChangeType.UNRELATED);
        assertThat(decision.content()).isEmpty();
        assertThat(decision.reason()).contains("assunto diferente");
    }

    @Test
    void fallsBackToTextComparisonOnUnknownDecision() {
        ChangeClassifier.Decision decision = classifierReturning("""
                {"decision": "MAYBE", "content": "x", "reason": "y"}""")
                .classify(current, candidate);

        assertThat(decision.type()).isEqualTo(ChangeType.CONTRADICTED);
    }

    @Test
    void fallsBackToTextComparisonWhenLlmFails() {
        ChangeClassifier.Decision decision = classifierFailing().classify(current, candidate);

        assertThat(decision.type()).isEqualTo(ChangeType.CONTRADICTED);
        assertThat(decision.content()).isEqualTo("Mora em Florianópolis.");
    }
}
