package ch.ost.cal.ade.plugins.archunit;

import ch.ost.cal.ade.plugins.archunit.spec.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class ArchUnitPluginTest {

    private final InputStream originalIn = System.in;
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
    private final ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();

    @BeforeEach
    void redirectStreams() {
        System.setOut(new PrintStream(capturedOut));
        System.setErr(new PrintStream(capturedErr));
    }

    @AfterEach
    void restoreStreams() {
        System.setIn(originalIn);
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private void setStdin(Spec spec) {
        System.setIn(new ByteArrayInputStream(spec.toByteArray()));
    }

    @Test
    void run_fileRulesInSpec_warnsAboutSkippedCount() {
        // given
        Spec spec = Spec.newBuilder()
                .setMode(InvocationMode.MODE_COMPILE)
                .setAdr(Adr.newBuilder().setId("ADR-001").setTitle("Test").build())
                .addRules(Rule.newBuilder().setName("readme_exists").setIsFileRule(true).build())
                .addRules(Rule.newBuilder().setName("changelog_exists").setIsFileRule(true).build())
                .build();
        setStdin(spec);

        // when
        new ArchUnitPlugin().run(new String[]{});

        // then
        assertThat(capturedErr.toString()).contains("2 rule(s) skipped");
    }

    @Test
    void run_invalidProtobufOnStdin_returnsOneWithErrorMessage() {
        // given
        System.setIn(new ByteArrayInputStream(new byte[]{0x01, 0x02, 0x03}));

        // when
        int exitCode = new ArchUnitPlugin().run(new String[]{});

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(capturedErr.toString()).contains("cannot read Spec from stdin");
    }

    @Test
    void run_unresolvableRuleKind_returnsOneWithErrorMessage() {
        // given — RULE_UNSPECIFIED is the default when no kind is set; RuleTranslator rejects it
        Spec spec = Spec.newBuilder()
                .setMode(InvocationMode.MODE_COMPILE)
                .setAdr(Adr.newBuilder().setId("ADR-001").setTitle("Test").build())
                .addRules(Rule.newBuilder().setName("bad_rule").setKind(RuleKind.RULE_UNSPECIFIED).build())
                .build();
        setStdin(spec);

        // when
        int exitCode = new ArchUnitPlugin().run(new String[]{});

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(capturedErr.toString()).contains("error:");
    }
}
