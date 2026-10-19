package com.polon.evomemory.application.assistant;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polon.evomemory.application.ports.LlmPort;
import com.polon.evomemory.application.ports.LlmRequest;
import com.polon.evomemory.config.LlmProperties;
import com.polon.evomemory.domain.memory.Memory;

/**
 * Filtro de relevância pelo modelo juiz: resolve o caso que filtros
 * determinísticos não sustentam (benchmark 19/10: distratores a ±0,04 da
 * memória correta e Gram de embeddings denso). Uma chamada por consulta.
 *
 * O resultado é {@code Optional}: {@code empty} significa falha (resposta
 * ilegível) e o chamador deve degradar para a ordenação por score;
 * presentes — mesmo vazio — é decisão usável do juiz.
 *
 * Conteúdo das memórias é dado não confiável: o prompt instrui resposta
 * somente em JSON, o parser aceita apenas números dentro do intervalo
 * informado e o chamador mantém o teto de contexto — a pior coisa que uma
 * memória maliciosa pode causar é adiantar o comportamento legado.
 */
@Service
public class JudgeReranker {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private static final String SYSTEM_PROMPT = """
            Você é um filtro de relevância de memórias do usuário. Dada a mensagem
            atual e uma lista numerada de memórias, escolha APENAS as memórias que
            ajudam diretamente a responder ou contextualizar a mensagem.

            Critérios:
            - Relevância por composição de significado, não por palavra em comum.
            - Rotinas, família, hobbies e planos só entram se a mensagem tratar deles.
            - Seja conservador: memória que não apoia diretamente fica fora.
            - Não devolver nada que não esteja na lista.

            Responda SÓ com JSON, sem acrescentar outra coisa:
            {"relevant": [<números>]}
            """;

    private final LlmPort llmPort;
    private final LlmProperties properties;

    public JudgeReranker(LlmPort llmPort, LlmProperties properties) {
        this.llmPort = llmPort;
        this.properties = properties;
    }

    /**
     * @param question mensagem atual do usuário
     * @param candidates memórias candidatas já ordenadas por score (cortadas)
     * @return ids escolhidos pelo juiz, ou vazio se a resposta for ilegível
     */
    public Optional<List<UUID>> select(String question, List<Memory> candidates) {
        if (candidates.isEmpty()) {
            return Optional.of(List.of());
        }
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            Memory memory = candidates.get(i);
            list.append(i + 1).append(". [").append(memory.getKind()).append("] ")
                    .append(memory.getContent()).append("\n");
        }
        String answer;
        try {
            answer = llmPort.generate(new LlmRequest(
                    properties.getJudgeModel(), SYSTEM_PROMPT,
                    "Mensagem atual:\n" + question + "\n\nMemórias:\n" + list, 0.0));
        } catch (RuntimeException error) {
            return Optional.empty();
        }
        return parse(answer, candidates.size())
                .map(numbers -> {
                    Set<UUID> selected = new LinkedHashSet<>();
                    for (int number : numbers) {
                        selected.add(candidates.get(number - 1).getId());
                    }
                    return new ArrayList<>(selected);
                });
    }

    /** Aceita JSON; sem JSON válido, procura números após "relevant". Vazio ou fora do intervalo = nada. */
    Optional<List<Integer>> parse(String answer, int candidateCount) {
        if (answer == null || answer.isBlank()) {
            return Optional.empty();
        }
        String text = answer.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (end < 0) {
            end = text.length() - 1;
        }
        List<Integer> numbers = new ArrayList<>();
        if (start >= 0 && end > start) {
            try {
                JsonNode relevant = MAPPER.readTree(text.substring(start, end + 1)).path("relevant");
                if (relevant.isArray()) {
                    for (JsonNode item : relevant) {
                        if (item.isInt() || item.isLong()) {
                            numbers.add(item.intValue());
                        }
                    }
                    return keepValid(numbers, candidateCount);
                }
            } catch (Exception ignored) {
                // cai para a varredura por números abaixo
            }
        }
        int marker = text.indexOf("relevant", Math.max(0, end - 100));
        if (marker >= 0 && marker < text.length()) {
            Matcher matcher = NUMBER.matcher(text.substring(marker, Math.min(end + 1, text.length())));
            while (matcher.find()) {
                numbers.add(Integer.parseInt(matcher.group()));
            }
        }
        // Varredura sem números não é decisão: é lixo ilegível → falha.
        if (numbers.isEmpty()) {
            return Optional.empty();
        }
        return keepValid(numbers, candidateCount);
    }

    private Optional<List<Integer>> keepValid(List<Integer> numbers, int candidateCount) {
        List<Integer> valid = numbers.stream()
                .filter(n -> n >= 1 && n <= candidateCount)
                .distinct()
                .toList();
        if (valid.size() != numbers.size()) {
            return Optional.empty();
        }
        return Optional.of(valid);
    }
}
