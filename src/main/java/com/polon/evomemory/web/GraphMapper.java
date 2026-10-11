package com.polon.evomemory.web;

import java.util.Map;
import com.polon.evomemory.memory.SemanticMemory;

public class GraphMapper {
    public static Map<String, Object> map(SemanticMemory m) {
        return Map.of(
                "id", m.getId(),
                "kind", m.getKind(),
                "subject_key", m.getSubjectKey(),
                "content", m.getContent(),
                "wing", m.getWing() != null ? m.getWing() : "general",
                "version", m.getVersion(),
                "conf", m.getConfidence(),
                "valid_until", m.getValidUntil(),
                "last_confirmed", m.getLastConfirmedAt()
        );
    }
}
