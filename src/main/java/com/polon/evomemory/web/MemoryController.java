package com.polon.evomemory.web;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.polon.evomemory.memory.MemoryService;

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
}
