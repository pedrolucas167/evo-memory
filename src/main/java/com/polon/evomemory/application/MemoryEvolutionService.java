package com.polon.evomemory.application;

import com.polon.evomemory.domain.memory.ChangeType;
import com.polon.evomemory.domain.memory.MemoryEvolution;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class MemoryEvolutionService {

    public MemoryEvolution.State evolve(MemoryEvolution.State current, ChangeType change,
                                        String content, double confidence, Instant now) {
        return MemoryEvolution.evolve(current, change, content, confidence, now);
    }
}
