package com.polon.evomemory.infrastructure.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.polon.evomemory.config.LlmProperties;
import com.polon.evomemory.config.RetrievalProperties;

@RestController
public class ExperimentController {
    private final LlmProperties llm;
    private final RetrievalProperties retrieval;

    public ExperimentController(LlmProperties llm, RetrievalProperties retrieval) {
        this.llm = llm;
        this.retrieval = retrieval;
    }

    @GetMapping("/api/experiments/{userId}/configuration")
    public Map<String, Object> configuration(@PathVariable String userId) {
        return Map.of("models", Map.of("chat", llm.getChatModel(), "extraction", llm.getExtractionModel(),
                        "judge", llm.getJudgeModel(), "embedding", llm.getEmbeddingModel()),
                "retrieval", Map.of("topK", retrieval.getTopK(), "minSimilarity", retrieval.getMinSimilarity(),
                        "relativeMargin", retrieval.getRelativeMargin()),
                "evolution", "versioned-llm-judge",
                "framework", org.springframework.boot.SpringBootVersion.getVersion());
    }
}
