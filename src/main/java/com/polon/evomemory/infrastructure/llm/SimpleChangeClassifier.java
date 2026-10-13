package com.polon.evomemory.infrastructure.llm;
import com.polon.evomemory.application.ports.ChangeClassifier;
import com.polon.evomemory.domain.memory.Memory;
import com.polon.evomemory.domain.memory.CandidateMemory;
import com.polon.evomemory.domain.memory.ChangeType;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
class SimpleChangeClassifier implements ChangeClassifier {

    @Override
    public Decision classify(Memory current, CandidateMemory candidate) {
        if (normalize(current.getContent()).equals(normalize(candidate.content()))) {
            return new Decision(ChangeType.CONFIRMED, current.getContent(), "mesma informação observada novamente");
        }
        return new Decision(ChangeType.CONTRADICTED, candidate.content(),
                "conteúdo divergente (comparação textual, usada quando o juiz via LLM falha)");
    }

    private static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
