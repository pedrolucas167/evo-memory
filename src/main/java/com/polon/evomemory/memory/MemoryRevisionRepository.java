package com.polon.evomemory.memory;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryRevisionRepository extends JpaRepository<MemoryRevision, UUID> {

    List<MemoryRevision> findByMemoryIdOrderByVersionAsc(UUID memoryId);
}
