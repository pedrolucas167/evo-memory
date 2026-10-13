package com.polon.evomemory.infrastructure.persistence;
import com.polon.evomemory.domain.memory.MemoryStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SemanticMemoryRepository extends JpaRepository<SemanticMemory, UUID> {

    Optional<SemanticMemory> findByUserIdAndSubjectKey(String userId, String subjectKey);

    Optional<SemanticMemory> findByIdAndUserId(UUID id, String userId);

    List<SemanticMemory> findByUserIdAndStatusOrderByConfidenceDesc(String userId, MemoryStatus status);
}
