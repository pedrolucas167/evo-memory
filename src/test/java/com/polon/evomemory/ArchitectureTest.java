package com.polon.evomemory;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ArchitectureTest {
    @Test
    void domainAndApplicationDoNotImportInfrastructure() throws Exception {
        for (String layer : new String[]{"domain", "application"}) {
            try (var files = Files.walk(Path.of("src/main/java/com/polon/evomemory", layer))) {
                for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    assertThat(source).as(file.toString()).doesNotContain("import com.polon.evomemory.infrastructure",
                            "import jakarta.persistence");
                    if (layer.equals("domain")) assertThat(source).doesNotContain("import org.springframework");
                }
            }
        }
    }
}
