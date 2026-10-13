package com.polon.evomemory.application.ports;

import com.polon.evomemory.domain.memory.MemoryRevisionDomain;
import com.polon.evomemory.domain.memory.Memory;
import com.polon.evomemory.domain.memory.Episode;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface MemoryStorePort {
    void saveRevision(MemoryRevisionDomain revision);
    Episode saveEpisode(Episode episode);
    Memory saveMemory(Memory memory);
    Optional<Memory> findBySubject(String userId, String subjectKey);
    Optional<Memory> findOwned(String userId, UUID memoryId);
    List<Memory> findActiveByUser(String userId);
    List<MemoryRevisionDomain> history(UUID memoryId);
    void flush();
}
