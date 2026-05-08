package io.riskplatform.monolith.unit;

import io.riskplatform.monolith.repository.MlScorerHttpAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MlScorerHttpAdapter in disabled mode (no ML_SCORER_URL set).
 */
class MlScorerHttpAdapterTest {

    private final MlScorerHttpAdapter adapter = new MlScorerHttpAdapter();

    @Test
    void score_returnsDefaultScore_whenDisabled() throws Exception {
        // ML_SCORER_URL not set → returns 0.5 default
        double score = adapter.score("c-1", "tx-001", 10000L);
        assertThat(score).isEqualTo(0.5);
    }

    @Test
    void score_returnsBetweenZeroAndOne() throws Exception {
        double score = adapter.score("c-2", "tx-002", 50000L);
        assertThat(score).isBetween(0.0, 1.0);
    }
}
