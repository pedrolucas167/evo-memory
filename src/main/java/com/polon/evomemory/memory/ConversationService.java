package com.polon.evomemory.memory;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.polon.evomemory.web.ChatResponse;
import com.polon.evomemory.web.MemoryView;

@Service
public class ConversationService {

    private static final int SUMMARY_LIMIT = 1000;

    private final MemoryService memoryService;
    private final ExtractionService extractionService;

    public ConversationService(MemoryService memoryService, ExtractionService extractionService) {
        this.memoryService = memoryService;
        this.extractionService = extractionService;
    }

    @Transactional
    public ChatResponse process(String userId, String conversationId, List<String> messages) {
        String summary = messages.stream().collect(Collectors.joining(" | "));
        if (summary.length() > SUMMARY_LIMIT) {
            summary = summary.substring(0, SUMMARY_LIMIT);
        }

        Episode episode = memoryService.recordEpisode(userId, conversationId, summary, EpisodeSource.CHAT);
        List<CandidateMemory> candidates = extractionService.extract(messages, memoryService.recallActive(userId));

        List<MemoryView> memories = candidates.stream()
                .map(candidate -> memoryService.observe(userId, episode, candidate))
                .map(MemoryView::from)
                .toList();
        return new ChatResponse(episode.getId(), memories);
    }
}
