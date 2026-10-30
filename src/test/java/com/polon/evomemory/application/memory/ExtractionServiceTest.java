package com.polon.evomemory.application.memory;
import com.polon.evomemory.domain.memory.Memory;
import com.polon.evomemory.domain.memory.MemoryKind;
import com.polon.evomemory.domain.memory.CandidateMemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polon.evomemory.config.LlmProperties;
import com.polon.evomemory.application.ports.LlmClient;

class ExtractionServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmProperties properties = new LlmProperties();

    private ExtractionService service(String rawResponse) {
        properties.setExtractionModel("test-model");
        LlmClient client = request -> {
            assertThat(request.model()).isEqualTo("test-model");
            assertThat(request.temperature()).isZero();
            return rawResponse;
        };
        return new ExtractionService(client, properties, mapper);
    }

    @Test
    void parseMemoriesFromPlainJson() {
        String json = """
                {"memories": [
                  {"kind": "INTEREST", "subject_key": "sistemas-distribuidos", "content": "Está estudando sistemas distribuídos.", "confidence": 0.7}
                ]}""";

        List<CandidateMemory> memories = service(json).extract(List.of("Estou estudando sistemas distribuídos."), List.of());

        assertThat(memories).hasSize(1);
        CandidateMemory memory = memories.get(0);
        assertThat(memory.kind()).isEqualTo(MemoryKind.INTEREST);
        assertThat(memory.subjectKey()).isEqualTo("sistemas-distribuidos");
        assertThat(memory.content()).isEqualTo("Está estudando sistemas distribuídos.");
        assertThat(memory.confidence()).isEqualTo(0.7);
    }

    @Test
    void parseMemoriesInsideMarkdownFence() {
        String json = """
                ```json
                {"memories": [{"kind": "PROJECT", "subject_key": "evo-memory", "content": "Está construindo o evo-memory.", "confidence": 0.8}]}
                ```""";

        List<CandidateMemory> memories = service(json).extract(List.of("estou construindo"), List.of());

        assertThat(memories).hasSize(1);
        assertThat(memories.get(0).kind()).isEqualTo(MemoryKind.PROJECT);
    }

    @Test
    void returnsEmptyListWhenNoRelevantContent() {
        List<CandidateMemory> memories = service("{\"memories\": []}")
                .extract(List.of("Qual a diferença entre SQL e NoSQL?"), List.of());

        assertThat(memories).isEmpty();
    }

    @Test
    void ignoresEntryWithUnknownKind() {
        String json = """
                {"memories": [
                  {"kind": "OPINION", "subject_key": "x", "content": "algo", "confidence": 0.5},
                  {"kind": "FACT", "subject_key": "cidade", "content": "Mora em Curitiba.", "confidence": 0.9}
                ]}""";

        List<CandidateMemory> memories = service(json).extract(List.of("msg"), List.of());

        assertThat(memories).hasSize(1);
        assertThat(memories.get(0).subjectKey()).isEqualTo("cidade");
    }

    @Test
    void ignoresEntryMissingSubjectKey() {
        String json = """
                {"memories": [
                  {"kind": "FACT", "subject_key": "", "content": "sem chave", "confidence": 0.5}
                ]}""";

        List<CandidateMemory> memories = service(json).extract(List.of("msg"), List.of());

        assertThat(memories).isEmpty();
    }

    @Test
    void defaultsConfidenceToHalf() {
        String json = """
                {"memories": [{"kind": "FACT", "subject_key": "x", "content": "algo"}]}""";

        List<CandidateMemory> memories = service(json).extract(List.of("msg"), List.of());

        assertThat(memories.get(0).confidence()).isEqualTo(0.5);
    }

    @Test
    void lowercasesSubjectKey() {
        String json = """
                {"memories": [{"kind": "FACT", "subject_key": "Minha-Cidade", "content": "algo", "confidence": 0.5}]}""";

        List<CandidateMemory> memories = service(json).extract(List.of("msg"), List.of());

        assertThat(memories.get(0).subjectKey()).isEqualTo("minha-cidade");
    }

    @Test
    void includesExistingMemoriesInPrompt() {
        properties.setExtractionModel("test-model");
        Memory existing = new Memory(
                java.util.UUID.randomUUID(), "user", MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos.", 0.8, null, java.time.Instant.now());
        final String[] capturedPrompt = new String[1];
        LlmClient client = request -> {
            capturedPrompt[0] = request.userPrompt();
            return "{\"memories\": []}";
        };
        ExtractionService extraction = new ExtractionService(client, properties, mapper);

        extraction.extract(List.of("Continuando os estudos."), List.of(existing));

        assertThat(capturedPrompt[0]).contains("Memórias existentes");
        assertThat(capturedPrompt[0]).contains("sistemas-distribuidos");
        assertThat(capturedPrompt[0]).contains("Mensagens do usuário");
        assertThat(capturedPrompt[0]).contains("Continuando os estudos.");
    }

    @Test
    void excludesRetractedMemoriesFromPrompt() {
        properties.setExtractionModel("test-model");
        Memory retracted = new Memory(
                java.util.UUID.randomUUID(), "user", MemoryKind.FACT, "cpf",
                "CPF do usuário", 0.5, null, java.time.Instant.now());
        retracted.retract(java.time.Instant.now());
        final String[] capturedPrompt = new String[1];
        LlmClient client = request -> {
            capturedPrompt[0] = request.userPrompt();
            return "{\"memories\": []}";
        };
        ExtractionService extraction = new ExtractionService(client, properties, mapper);

        extraction.extract(List.of("msg"), List.of(retracted));

        assertThat(capturedPrompt[0]).doesNotContain("CPF");
    }

    @Test
    void parsesHealthAndPlanWithValidity() {
        String json = """
                {"memories": [
                  {"kind": "HEALTH", "subject_key": "alergia-amendoim", "content": "É alérgico a amendoim.", "confidence": 0.9, "valid_until": null},
                  {"kind": "PLAN", "subject_key": "viagem-japao", "content": "Vai viajar ao Japão em abril.", "confidence": 0.8, "valid_until": "2026-04-30"}
                ]}""";

        List<CandidateMemory> memories = service(json).extract(List.of("msg"), List.of());

        assertThat(memories).hasSize(2);
        assertThat(memories.get(0).kind()).isEqualTo(MemoryKind.HEALTH);
        assertThat(memories.get(0).validUntil()).isNull();
        assertThat(memories.get(1).kind()).isEqualTo(MemoryKind.PLAN);
        assertThat(memories.get(1).validUntil()).isEqualTo(java.time.LocalDate.of(2026, 4, 30));
    }

    @Test
    void ignoresInvalidValidityDate() {
        String json = """
                {"memories": [
                  {"kind": "PLAN", "subject_key": "viagem", "content": "Vai viajar.", "confidence": 0.8, "valid_until": "abril"}
                ]}""";

        List<CandidateMemory> memories = service(json).extract(List.of("msg"), List.of());

        assertThat(memories).hasSize(1);
        assertThat(memories.get(0).validUntil()).isNull();
    }

    @Test
    void throwsOnInvalidJson() {
        assertThatThrownBy(() -> service("isto não é json").extract(List.of("msg"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closingPromptAddsClosureCandidateAndStateRulesWhenSubjectClosingIsEnabled() {
        properties.setExtractionModel("test-model");
        properties.setSubjectClosing(true);
        final String[] capturedSystem = new String[1];
        LlmClient client = request -> {
            capturedSystem[0] = request.systemPrompt();
            return "{\"memories\": []}";
        };

        new ExtractionService(client, properties, mapper).extract(List.of("msg"), List.of());

        // Frente 1.1: candidato de fechamento explícito (mesma chave da fase)
        // e estado novo com chave própria.
        assertThat(capturedSystem[0]).contains("candidato de FECHAMENTO");
        assertThat(capturedSystem[0]).contains("MESMO subject_key da memória existente");
        assertThat(capturedSystem[0]).contains("subject_key NOVO");
        // Frente 2 desligada: sem a regra de transição declarada.
        assertThat(capturedSystem[0]).doesNotContain("devolva sempre os DOIS candidatos");
    }

    @Test
    void closingSplitPromptAddsTheDeclaredTransitionRule() {
        // Frente 2 (pré-registro 30/10): transição "de X para Y" exige os dois
        // candidatos — fechamento com a MESMA chave e estado novo com chave
        // nova — porque o juiz não vê a mensagem crua para resgatar o estado.
        properties.setExtractionModel("test-model");
        properties.setSubjectClosing(true);
        properties.setClosingSplit(true);
        final String[] capturedSystem = new String[1];
        LlmClient client = request -> {
            capturedSystem[0] = request.systemPrompt();
            return "{\"memories\": []}";
        };

        new ExtractionService(client, properties, mapper).extract(List.of("msg"), List.of());

        assertThat(capturedSystem[0]).contains("transição declarada");
        assertThat(capturedSystem[0]).contains("devolva sempre os DOIS candidatos");
        assertThat(capturedSystem[0]).contains("MESMO subject_key da memória existente");
        assertThat(capturedSystem[0]).contains("Nunca devolva apenas o candidato de fechamento");
        // O corpo do fechamento 1.1 continua presente.
        assertThat(capturedSystem[0]).contains("candidato de FECHAMENTO");
    }

    @Test
    void closingSplitWithoutSubjectClosingKeepsHistoricalPrompt() {
        properties.setExtractionModel("test-model");
        properties.setSubjectClosing(false);
        properties.setClosingSplit(true);
        final String[] capturedSystem = new String[1];
        LlmClient client = request -> {
            capturedSystem[0] = request.systemPrompt();
            return "{\"memories\": []}";
        };

        new ExtractionService(client, properties, mapper).extract(List.of("msg"), List.of());

        // Sem fechamento a regra de troca não existe (a fase nem fecha).
        assertThat(capturedSystem[0]).doesNotContain("candidato de FECHAMENTO");
        assertThat(capturedSystem[0]).doesNotContain("devolva sempre os DOIS candidatos");
    }

    @Test
    void defaultPromptKeepsHistoricalBehavior() {
        properties.setExtractionModel("test-model");
        properties.setSubjectClosing(false);
        final String[] capturedSystem = new String[1];
        LlmClient client = request -> {
            capturedSystem[0] = request.systemPrompt();
            return "{\"memories\": []}";
        };

        new ExtractionService(client, properties, mapper).extract(List.of("msg"), List.of());

        assertThat(capturedSystem[0]).doesNotContain("candidato de FECHAMENTO");
    }
}
