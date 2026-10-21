package com.polon.evomemory.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SubjectMatcherTest {

    private final Instant now = Instant.parse("2026-10-13T00:00:00Z");

    private Memory memory(MemoryKind kind, String subjectKey, String content) {
        return new Memory(UUID.randomUUID(), "user", kind, subjectKey, content, 0.8, null, now);
    }

    private CandidateMemory candidate(MemoryKind kind, String subjectKey, String content) {
        return new CandidateMemory(kind, subjectKey, content, 0.7);
    }

    @Test
    void recognizesRenamedEntityAcrossKeys() {
        // Caso real do dossiê 2026-10-20: "Tobi" -> "Nina" sob chaves por nome.
        var old = memory(MemoryKind.FACT, "gato-tobi", "Adotou um gato chamado Tobi, que fugiu.");
        var observation = candidate(MemoryKind.FACT, "gata-nina",
                "Adotou uma gata chamada Nina para substituir Tobi.");

        assertThat(SubjectMatcher.overlapScore(old, observation))
                .isGreaterThanOrEqualTo(SubjectMatcher.MIN_OVERLAP);
    }

    @Test
    void differentKindsNeverMatch() {
        var fact = memory(MemoryKind.FACT, "gato-tobi", "Adotou um gato chamado Tobi, que fugiu.");
        var plan = memory(MemoryKind.PLAN, "gato-tobi", "Adotou um gato chamado Tobi, que fugiu.");
        var observation = candidate(MemoryKind.FACT, "gata-nina",
                "Adotou uma gata chamada Nina para substituir Tobi.");

        assertThat(SubjectMatcher.overlapScore(plan, observation)).isZero();
        assertThat(SubjectMatcher.overlapScore(fact, observation))
                .isGreaterThanOrEqualTo(SubjectMatcher.MIN_OVERLAP);
    }

    @Test
    void deathEventCitesTheNamedSubjectOfTheOtherSide() {
        // Passo real A->B do caso "gato-abc": a observação cita a entidade que
        // a memória nomeia ("a Mel morreu"), com um único token de conteúdo.
        var old = memory(MemoryKind.FACT, "gata-mel", "Tenho uma gata chamada Mel.");
        var observation = candidate(MemoryKind.FACT, "gato-tobi",
                "A Mel morreu e adotei um gato chamado Tobi.");

        assertThat(SubjectMatcher.related(old, observation)).isTrue();
        assertThat(SubjectMatcher.overlapScore(old, observation))
                .isLessThan(SubjectMatcher.MIN_OVERLAP);
    }

    @Test
    void singleSharedContentTokenWithoutSubjectCitationStaysBelowTheGate() {
        // Assuntos distintos: o único token comum ("violão") não é o assunto
        // de nenhum dos lados.
        var old = memory(MemoryKind.FACT, "hobby-musical", "Toca violão desde os oito anos.");
        var observation = candidate(MemoryKind.FACT, "compras",
                "Comprou um violão novo para praticar em casa.");

        assertThat(SubjectMatcher.related(old, observation)).isFalse();
        assertThat(SubjectMatcher.overlapScore(old, observation))
                .isLessThan(SubjectMatcher.MIN_OVERLAP);
    }

    @Test
    void fullyUnrelatedTextsScoreZero() {
        var old = memory(MemoryKind.FACT, "viagem-janeiro", "Vai viajar com os amigos em janeiro.");
        var observation = candidate(MemoryKind.FACT, "curso-ingles",
                "Começou aulas de inglês na terça.");

        assertThat(SubjectMatcher.overlapScore(old, observation)).isZero();
    }

    @Test
    void accentsAreNormalizedBeforeCounting() {
        var old = memory(MemoryKind.FACT, "pelada-sabado", "Jogo futebol todo sábado.");
        var observation = candidate(MemoryKind.FACT, "futebol", "Futebol de sábado está suspenso.");

        assertThat(SubjectMatcher.overlapScore(old, observation))
                .isGreaterThanOrEqualTo(SubjectMatcher.MIN_OVERLAP);
    }

    @Test
    void stopwordsDoNotInflateTheScore() {
        var old = memory(MemoryKind.FACT, "rotina", "Muda de rotina cada mês.");
        var observation = candidate(MemoryKind.FACT, "trabalho", "Trocou de trabalho este mês.");

        // "muda"/"trocou", "rotina"/"trabalho": só "mês" comum, e nenhum lado
        // cita o assunto do outro — não pode forçar par.
        assertThat(SubjectMatcher.overlapScore(old, observation))
                .isLessThan(SubjectMatcher.MIN_OVERLAP);
        assertThat(SubjectMatcher.related(old, observation)).isFalse();
    }
}
