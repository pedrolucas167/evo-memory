package com.polon.evomemory.infrastructure.web;

import java.util.Map;
import com.polon.evomemory.domain.memory.Memory;

public class GraphMapper {
    public static Map<String, Object> map(Memory m) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", m.getId());
        view.put("kind", m.getKind());
        view.put("subject_key", m.getSubjectKey());
        view.put("content", m.getContent());
        view.put("wing", m.getWing() != null ? m.getWing() : "general");
        view.put("version", m.getVersion());
        view.put("conf", m.getConfidence());
        view.put("valid_until", m.getValidUntil());
        view.put("last_confirmed", m.getLastConfirmedAt());
        return view;
    }
}
