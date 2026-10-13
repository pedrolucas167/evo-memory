package com.polon.evomemory.application.ports;
import com.polon.evomemory.domain.memory.Memory;
import com.polon.evomemory.domain.memory.CandidateMemory;
import com.polon.evomemory.domain.memory.ChangeType;

public interface ChangeClassifier {

    Decision classify(Memory current, CandidateMemory candidate);

    record Decision(ChangeType type, String content, String reason) {
    }
}
