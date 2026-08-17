package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KaosApplicationTest {
    @Test
    void identifiesTheApplicationBaselineWithoutClaimingAProductCapability() {
        assertEquals("KAOS application baseline is running.", KaosApplication.startupMessage());
    }
}
