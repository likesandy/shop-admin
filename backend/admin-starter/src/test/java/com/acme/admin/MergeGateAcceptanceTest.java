package com.acme.admin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;

/** Temporary negative control for protected-branch acceptance; never merge. */
class MergeGateAcceptanceTest {
    @Test void intentionalFailureMustBlockMerge() {
        fail("Intentional merge-gate acceptance failure; never merge this test");
    }
}
