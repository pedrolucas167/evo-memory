package com.polon.evomemory.infrastructure.llm;
import com.polon.evomemory.application.ports.ChangeClassifier;
import com.polon.evomemory.domain.memory.Memory;
import com.polon.evomemory.domain.memory.CandidateMemory;
import com.polon.evomemory.domain.memory.ChangeType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polon.evomemory.config.LlmProperties;
import com.polon.evomemory.application.ports.LlmClient;
import com.polon.evomemory.application.ports.LlmRequest;

@Component
@Primary
class LlmChangeClassifier implements ChangeClassifier {

    private static final Logger log = LoggerFactory.getLogger(LlmChangeClassifier.class);

    private static final String SYSTEM_PROMPT = """
            Você decide como uma nova informação sobre o usuário altera uma memória já registrada sobre o mesmo assunto.

            Escolha uma decisão:
            - CONFIRMED: a nova informação repete o que a memória já diz.
            - COMPLEMENTED: a nova informação acrescenta detalhes sem contrariar a memória. O conteúdo deve combinar os dois.
            - CONTRADICTED: a nova informação substitui ou contraria a memória (mudança de fato, de opinião ou de decisão). O conteúdo deve refletir apenas o estado mais recente.
            - UNRELATED: a nova informação NÃO trata do assunto da memória (não repete, não acrescenta e não contraria); use apenas quando não houver relação temática.

            Responda apenas com JSON neste formato:
            {"decision": "CONFIRMED|COMPLEMENTED|CONTRADICTED|UNRELATED", "content": "frase curta em português", "reason": "motivo em uma frase"}""";

    /**
     * Fechamento de assunto (pré-registro 22/10): com a chave ligada, o juiz
     * reconhece a fase encerrada pela nova informação. A memória fechada deixa
     * de valer como um todo (em vez do conteúdo de CONTRADICTED).
     */
    private static final String SYSTEM_PROMPT_WITH_CLOSING = """
            Você decide como uma nova informação sobre o usuário altera uma memória já registrada sobre o mesmo assunto.

            Escolha uma decisão:
            - CONFIRMED: a nova informação repete o que a memória já diz.
            - COMPLEMENTED: a nova informação acrescenta detalhes sem contrariar a memória. O conteúdo deve combinar os dois.
            - CONTRADICTED: a nova informação substitui ou contraria a memória (mudança de fato, de opinião ou de decisão), sem encerrar a fase que a memória descreve. O conteúdo deve refletir apenas o estado mais recente.
            - CLOSED: a nova informação encerra a fase que a memória descreve (algo ou alguém morreu, partiu, foi vendido, terminou, foi cancelado ou concluído). A memória inteira deixa de valer; no conteúdo, descreva apenas o estado que substitui a fase encerrada, sem narrar a transição nem citar a fase fechada.
            - UNRELATED: a nova informação NÃO trata do assunto da memória (não repete, não acrescenta, não contraria e não encerra); use apenas quando não houver relação temática.

            Responda apenas com JSON neste formato:
            {"decision": "CONFIRMED|COMPLEMENTED|CONTRADICTED|CLOSED|UNRELATED", "content": "frase curta em português", "reason": "motivo em uma frase"}""";

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final ObjectMapper mapper;
    private final SimpleChangeClassifier fallback;

    LlmChangeClassifier(LlmClient llmClient, LlmProperties properties, ObjectMapper mapper,
                        SimpleChangeClassifier fallback) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.mapper = mapper;
        this.fallback = fallback;
    }

    @Override
    public Decision classify(Memory current, CandidateMemory candidate) {
        String userPrompt = "Memória atual: \"" + current.getContent() + "\"\n"
                + "Nova informação: \"" + candidate.content() + "\"";
        try {
            String raw = llmClient.complete(new LlmRequest(
                    properties.getJudgeModel(),
                    properties.isSubjectClosing() ? SYSTEM_PROMPT_WITH_CLOSING : SYSTEM_PROMPT,
                    userPrompt, 0.0));
            return parse(raw, current, candidate);
        } catch (RuntimeException error) {
            log.warn("Juiz de mudancas falhou, usando comparacao textual");
            return fallback.classify(current, candidate);
        }
    }

    private Decision parse(String raw, Memory current, CandidateMemory candidate) {
        String cleaned = raw.trim().replaceAll("(?s)^```(?:json)?\\s*|\\s*```$", "");
        JsonNode node;
        try {
            node = mapper.readTree(cleaned);
        } catch (Exception error) {
            throw new IllegalArgumentException("Resposta do juiz nao era JSON valido");
        }

        String decisionText = node.path("decision").asText("");
        if (decisionText.equals("CLOSED") && !properties.isSubjectClosing()) {
            // Chave desligada: o veredito não existe no contrato corrente (comportamento sem mudança).
            throw new IllegalArgumentException("Decisao desconhecida do juiz");
        }
        ChangeType type = switch (decisionText) {
            case "CONFIRMED" -> ChangeType.CONFIRMED;
            case "COMPLEMENTED" -> ChangeType.COMPLEMENTED;
            case "CONTRADICTED" -> ChangeType.CONTRADICTED;
            case "CLOSED" -> ChangeType.CLOSED;
            case "UNRELATED" -> ChangeType.UNRELATED;
            default -> throw new IllegalArgumentException("Decisao desconhecida do juiz");
        };

        String content = node.path("content").asText("").trim();
        if (type == ChangeType.CONFIRMED) {
            content = current.getContent();
        } else if (type == ChangeType.UNRELATED) {
            content = "";
        } else if (content.isEmpty()) {
            content = candidate.content();
        }

        return new Decision(type, content, "juiz: " + node.path("reason").asText("sem motivo informado"));
    }
}
