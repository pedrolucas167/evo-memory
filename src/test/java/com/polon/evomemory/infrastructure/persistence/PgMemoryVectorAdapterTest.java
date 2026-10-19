package com.polon.evomemory.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PgMemoryVectorAdapterTest {

    @Test
    void parsesPgvectorLiteralBackToVector() {
        float[] vector = {0.25f, -1.5f, 3.0f};
        assertThat(PgMemoryVectorAdapter.parseLiteral(PgMemoryVectorAdapter.toLiteral(vector)))
                .isEqualTo(vector);
    }

    @Test
    void parsesLiteralWithSpacesAndEmptyVector() {
        assertThat(PgMemoryVectorAdapter.parseLiteral("[0.5, 1.0, -0.5]"))
                .isEqualTo(new float[] {0.5f, 1.0f, -0.5f});
        assertThat(PgMemoryVectorAdapter.parseLiteral("[]")).isEmpty();
    }
}
