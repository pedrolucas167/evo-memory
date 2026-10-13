package com.polon.evomemory.application.assistant;

import java.util.*;

public class SynonymExpander {

    private static final Map<String, Set<String>> FAMILIES = Map.ofEntries(
        Map.entry("violao", Set.of("violino", "violoncelo", "instrumento", "musica")),
        Map.entry("violino", Set.of("violao", "violoncelo", "instrumento", "musica")),
        Map.entry("professor", Set.of("historia", "ensino", "educacao", "revolucao")),
        Map.entry("historia", Set.of("professor", "revolucao", "francesa", "ensino")),
        Map.entry("revolucao", Set.of("historia", "francesa", "professor")),
        Map.entry("bairros", Set.of("lisboa", "portugal", "moradia", "cidade")),
        Map.entry("lisboa", Set.of("bairros", "portugal", "moradia", "viagem")),
        Map.entry("filho", Set.of("filhos", "criancas", "pequenos", "familia")),
        Map.entry("filhos", Set.of("filho", "criancas", "pequenos", "familia")),
        Map.entry("criancas", Set.of("filhos", "filho", "pequenos", "familia")),
        Map.entry("corrida", Set.of("maratona", "esporte", "treino", "corrida")),
        Map.entry("maratona", Set.of("corrida", "esporte", "treino", "42")),
        Map.entry("nadar", Set.of("agua", "piscina", "nado", "exercicio")),
        Map.entry("agua", Set.of("nadar", "piscina", "nado", "exercicio")),
        Map.entry("romance", Set.of("escrever", "livro", "texto", "destravar")),
        Map.entry("escrever", Set.of("romance", "livro", "texto", "destravar"))
    );

    public static Set<String> expand(Set<String> words) {
        Set<String> out = new HashSet<>(words);
        for (String w : words) {
            Set<String> fam = FAMILIES.getOrDefault(w, Collections.emptySet());
            out.addAll(fam);
        }
        return out;
    }
}
