package com.polon.evomemory.web;

import java.util.Map;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.polon.evomemory.memory.MemoryService;
import com.polon.evomemory.memory.SemanticMemory;

import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping("/{userId}/reindex")
    public ReindexResult reindex(@NotBlank @PathVariable String userId) {
        return memoryService.reindex(userId);
    }

    @GetMapping("/{userId}/graph")
    public ResponseEntity<Map<String, Object>> graph(@PathVariable String userId) {
        List<SemanticMemory> active = memoryService.recallActive(userId);
        return ResponseEntity.ok(Map.of("user", userId, "memories",
                active.stream().map(m -> Map.of("id", m.getId(), "kind", m.getKind(),
                        "subject_key", m.getSubjectKey(), "content", m.getContent(),
                        "wing", m.getWing() != null ? m.getWing() : "general", "version", m.getVersion(),
                        "conf", m.getConfidence(), "valid_until", m.getValidUntil(),
                        "last_confirmed", m.getLastConfirmedAt())).toList(),
                "count", active.size()));
    }
}
