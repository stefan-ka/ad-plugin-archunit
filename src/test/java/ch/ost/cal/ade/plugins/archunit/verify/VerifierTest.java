package ch.ost.cal.ade.plugins.archunit.verify;

import ch.ost.cal.ade.plugins.archunit.spec.Adr;
import ch.ost.cal.ade.plugins.archunit.spec.InvocationMode;
import ch.ost.cal.ade.plugins.archunit.spec.Spec;
import ch.ost.cal.ade.plugins.archunit.exceptions.BuildException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerifierTest {

    private final Verifier verifier = new Verifier(Spec.newBuilder()
            .setMode(InvocationMode.MODE_VERIFY)
            .setAdr(Adr.newBuilder().setId("ADR-001").setTitle("Test").build())
            .build());

    private String captureStderr(Runnable action) {
        PrintStream original = System.err;
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capture));
        try {
            action.run();
        } finally {
            System.setErr(original);
        }
        return capture.toString();
    }

    @Test
    void reportResults_allPassed_noExceptionAndOutputsPassedLines() throws BuildException {
        // given
        List<TestsRunner.VerifyResult> results = List.of(
                new TestsRunner.VerifyResult("no_deps", true, ""),
                new TestsRunner.VerifyResult("no_cycles", true, "")
        );

        // when
        String stderr = captureStderr(() -> {
            try { verifier.reportResults(results); } catch (BuildException e) { throw new RuntimeException(e); }
        });

        // then
        assertThat(stderr)
                .contains("passed [no_deps]")
                .contains("passed [no_cycles]");
    }

    @Test
    void reportResults_oneFailed_throwsBuildException() {
        // given
        List<TestsRunner.VerifyResult> results = List.of(
                new TestsRunner.VerifyResult("no_deps", false, "Dependency rule violated")
        );

        // when / then
        assertThatThrownBy(() -> verifier.reportResults(results))
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("one or more architecture rules failed");
    }

    @Test
    void reportResults_failedResult_includesRuleNameAndDetailInStderr() {
        // given
        List<TestsRunner.VerifyResult> results = List.of(
                new TestsRunner.VerifyResult("no_deps", false, "Dependency rule violated")
        );

        // when
        String stderr = captureStderr(() -> {
            try { verifier.reportResults(results); } catch (BuildException ignored) {}
        });

        // then
        assertThat(stderr)
                .contains("error: failed [no_deps]")
                .contains("Dependency rule violated");
    }

    @Test
    void reportResults_mixed_throwsAndReportsBothPassAndFail() {
        // given
        List<TestsRunner.VerifyResult> results = List.of(
                new TestsRunner.VerifyResult("no_deps", true, ""),
                new TestsRunner.VerifyResult("no_cycles", false, "Cycle found")
        );

        // when
        String stderr = captureStderr(() -> {
            try { verifier.reportResults(results); } catch (BuildException ignored) {}
        });

        // then
        assertThatThrownBy(() -> verifier.reportResults(results))
                .isInstanceOf(BuildException.class);
        assertThat(stderr)
                .contains("passed [no_deps]")
                .contains("error: failed [no_cycles]");
    }

    @Test
    void reportResults_emptyResults_noExceptionThrown() throws BuildException {
        // given
        List<TestsRunner.VerifyResult> results = List.of();

        // when / then — no exception
        verifier.reportResults(results);
    }
}
