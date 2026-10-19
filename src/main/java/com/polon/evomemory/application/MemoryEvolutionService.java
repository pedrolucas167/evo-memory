package com.polon.evomemory.application;

import com.polon.evomemory.domain.memory.ChangeType;
import com.polon.evomemory.domain.memory.MemoryEvolution;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class MemoryEvolutionService {

    public MemoryEvolution.State evolve(MemoryEvolution.State current, ChangeType change,
                                        String content, double confidence, Instant now) {
        return evolve(current, change, content, confidence, now, 0.0);
    }

    /**
     * Evolução com a leitura de atenção OLS (similarity view de arXiv:2504.09663).
     * A leitura ativa o ganho adicional de confiança em confirmações.
     */
    public MemoryEvolution.State evolve(MemoryEvolution.State current, ChangeType change,
                                        String content, double confidence, Instant now,
                                        double attention) {
        return MemoryEvolution.evolve(current, change, content, confidence, now, attention);
    }
}
