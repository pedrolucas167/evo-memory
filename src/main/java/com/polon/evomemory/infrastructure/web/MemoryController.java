package com.polon.evomemory.infrastructure.web;
import com.polon.evomemory.application.model.ReindexResult;

import java.util.Map;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RestController;

import com.polon.evomemory.application.memory.MemoryService;
import com.polon.evomemory.domain.memory.Memory;

import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    public record Correction(@jakarta.validation.constraints.NotBlank
                             @jakarta.validation.constraints.Size(max = 10000) String content) {}

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{userId}/{memoryId}")
    public com.polon.evomemory.application.model.MemoryView correct(@PathVariable String userId,
            @PathVariable java.util.UUID memoryId,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody Correction correction) {
        return com.polon.evomemory.application.model.MemoryView.from(
                memoryService.correct(userId, memoryId, correction.content()));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{userId}/{memoryId}")
    public com.polon.evomemory.application.model.MemoryView forget(@PathVariable String userId,
            @PathVariable java.util.UUID memoryId) {
        return com.polon.evomemory.application.model.MemoryView.from(memoryService.forget(userId, memoryId));
    }

    @GetMapping("/{userId}/{memoryId}/history")
    public List<com.polon.evomemory.domain.memory.MemoryRevisionDomain> history(@PathVariable String userId,
            @PathVariable java.util.UUID memoryId) {
        return memoryService.history(userId, memoryId);
    }

    @PostMapping("/{userId}/reindex")
    public ReindexResult reindex(@NotBlank @PathVariable String userId, HttpServletRequest request) {
        String tokenUser = (String) request.getAttribute("authUserId");
        if (tokenUser != null && !tokenUser.equals(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "userId não corresponde ao token");
        }
        return memoryService.reindex(userId);
    }

    @GetMapping("/{userId}/graph")
    public ResponseEntity<Map<String, Object>> graph(@PathVariable String userId) {
        List<Memory> active = memoryService.recallActive(userId);
        return ResponseEntity.ok(Map.of("user", userId, "memories",
                active.stream().map(GraphMapper::map).toList(),
                "count", active.size()));
    }
}
