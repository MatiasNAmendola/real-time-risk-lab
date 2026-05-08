package io.riskplatform.k8stest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Resource path helpers shared by tests. */
final class TestPaths {
    private TestPaths() {}

    /** Absolute filesystem path to {@code values-test.yaml} for `helm -f`. */
    static String testValues() {
        // Tests are executed from the gradle module dir.
        Path candidate = Paths.get("src/test/resources/helm/values-test.yaml")
                .toAbsolutePath();
        if (!Files.exists(candidate)) {
            throw new IllegalStateException("Missing test values file: " + candidate);
        }
        return candidate.toString();
    }
}
