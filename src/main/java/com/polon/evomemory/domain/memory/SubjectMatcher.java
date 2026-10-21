package com.polon.evomemory.domain.memory;

import java.text.Normalizer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Pareamento determinístico de assunto: mede sobreposição de palavras
 * significantes entre uma memória e uma observação candidata, para reconhecer
 * o mesmo tema de vida sob chaves de assunto diferentes (ex.: entidade
 * renomeada "Tobi" -> "Nina" — o extrator gera chaves por nome e a cadeia de
 * contradição por sujeito nunca encadeia; dossiê 2026-10-20).
 *
 * O componente só PROPÕE candidatos — a decisão de como a observação altera a
 * memória segue do juiz (ChangeClassifier). Falso positivo custa uma
 * verificação do juiz e nunca altera dado sozinho.
 */
public final class SubjectMatcher {

    /** Sobreposição mínima de conteúdo para o candidato entrar na fila do juiz. */
    public static final int MIN_OVERLAP = 2;

    private static final Set<String> STOPWORDS = Set.of(
            "que", "para", "com", "sem", "como", "mais", "menos", "muito", "pouco",
            "sobre", "entre", "pela", "pelo", "pelas", "pelos", "uma", "uns", "umas",
            "dos", "das", "nos", "nas", "aos", "sua", "seu", "suas", "seus", "pode",
            "houve", "ser", "tera", "estava", "estao", "tambem", "ainda", "agora",
            "antes", "depois", "todos", "todas", "todo", "toda", "the");

    private SubjectMatcher() {
    }

    /**
     * @return número de palavras significantes compartilhadas entre
     *         (assunto + conteúdo) da memória e (assunto + conteúdo) da
     *         observação; 0 quando os tipos de memória divergem
     */
    public static int overlapScore(Memory memory, CandidateMemory candidate) {
        if (memory.getKind() != candidate.kind()) {
            return 0;
        }
        Set<String> fromMemory = tokens(memory.getSubjectKey() + " " + memory.getContent());
        return countShared(fromMemory, tokens(candidate.subjectKey() + " " + candidate.content()));
    }

    /**
     * Candidato à fila do juiz: sobreposição de conteúdo >= {@link #MIN_OVERLAP}
     * OU sinal de identidade — um token do assunto de um lado aparece do outro
     * (ex.: a observação "a Mel morreu" cita a entidade que a memória nomeia).
     * Citação de assunto é o sinal mais forte de tema comum e vale sozinha; a
     * decisão de alterar a memória segue do juiz.
     */
    public static boolean related(Memory memory, CandidateMemory candidate) {
        if (memory.getKind() != candidate.kind()) {
            return false;
        }
        Set<String> memoryTokens = tokens(memory.getSubjectKey() + " " + memory.getContent());
        Set<String> candidateTokens = tokens(candidate.subjectKey() + " " + candidate.content());
        if (countShared(memoryTokens, candidateTokens) >= MIN_OVERLAP) {
            return true;
        }
        return !Collections.disjoint(tokens(memory.getSubjectKey()), candidateTokens)
                || !Collections.disjoint(tokens(candidate.subjectKey()), memoryTokens);
    }

    private static int countShared(Set<String> fromMemory, Set<String> fromCandidate) {
        Set<String> copy = new HashSet<>(fromMemory);
        copy.retainAll(fromCandidate);
        return copy.size();
    }

    private static Set<String> tokens(String text) {
        String flat = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        Set<String> tokens = new HashSet<>();
        for (String token : flat.split("[\\W_]+")) {
            if (token.length() >= 3 && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
