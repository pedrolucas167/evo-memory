package com.polon.evomemory.web;

import java.util.Map;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatusException;
import org.springframework.web.bind.annotation.RestController;

import com.polon.evomemory.auth.AuthService;
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
        List<SemanticMemory> active = memoryService.recallActive(userId);
        return ResponseEntity.ok(Map.of("user", userId, "memories",
                active.stream().map(GraphMapper::map).toList(),
                "count", active.size()));
    }
}
