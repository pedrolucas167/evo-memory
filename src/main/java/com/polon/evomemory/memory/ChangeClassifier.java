package com.polon.evomemory.memory;

public interface ChangeClassifier {

    Decision classify(SemanticMemory current, CandidateMemory candidate);

    record Decision(ChangeType type, String content, String reason) {
    }
}
