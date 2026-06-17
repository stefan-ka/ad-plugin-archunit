package ch.ost.cal.ade.plugins.archunit.verify;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestsRunnerTest {

    private final TestsRunner runner = new TestsRunner(Map.of());

    private BufferedReader readerOf(String text) {
        return new BufferedReader(new StringReader(text));
    }

    @Test
    void parseGradleOutput_singlePassed_returnsPassedResult() throws IOException {
        // given
        String output = "ArchitectureFrom_ADR_001Test > ADR_001_no_deps PASSED\n";
        Map<String, String> methodToRule = Map.of("ADR_001_no_deps", "no_deps");

        // when
        List<TestsRunner.VerifyResult> results = runner.parseGradleOutput(readerOf(output), methodToRule);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).ruleName()).isEqualTo("no_deps");
        assertThat(results.get(0).passed()).isTrue();
        assertThat(results.get(0).message()).isEmpty();
    }

    @Test
    void parseGradleOutput_singleFailed_returnsFailedResultWithMessage() throws IOException {
        // given
        String output = """
                ArchitectureFrom_ADR_001Test > ADR_001_no_cycles FAILED
                    AssertionError: Architecture Violation [Priority: MEDIUM]
                """;
        Map<String, String> methodToRule = Map.of("ADR_001_no_cycles", "no_cycles");

        // when
        List<TestsRunner.VerifyResult> results = runner.parseGradleOutput(readerOf(output), methodToRule);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).ruleName()).isEqualTo("no_cycles");
        assertThat(results.get(0).passed()).isFalse();
        assertThat(results.get(0).message()).contains("Architecture Violation");
    }

    @Test
    void parseGradleOutput_mixedResults_returnsAllResults() throws IOException {
        // given
        String output = """
                ArchitectureFrom_ADR_001Test > ADR_001_no_deps PASSED
                ArchitectureFrom_ADR_001Test > ADR_001_no_cycles FAILED
                    AssertionError: Cycle detected
                """;
        Map<String, String> methodToRule = Map.of(
                "ADR_001_no_deps", "no_deps",
                "ADR_001_no_cycles", "no_cycles"
        );

        // when
        List<TestsRunner.VerifyResult> results = runner.parseGradleOutput(readerOf(output), methodToRule);

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).passed()).isTrue();
        assertThat(results.get(1).passed()).isFalse();
    }

    @Test
    void parseGradleOutput_unmappedMethod_usesMethodNameAsRuleName() throws IOException {
        // given
        String output = "ArchitectureFrom_ADR_001Test > some_unknown_method PASSED\n";

        // when
        List<TestsRunner.VerifyResult> results = runner.parseGradleOutput(readerOf(output), Map.of());

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).ruleName()).isEqualTo("some_unknown_method");
    }

    @Test
    void parseMavenOutput_singleFailure_returnsFailedResultWithMessage() throws IOException {
        // given
        String output = """
                [ERROR] ADR_001_no_cycles  Time elapsed: 0.042 s  <<< FAILURE!
                AssertionError: Architecture Violation [Priority: MEDIUM]
                [INFO] BUILD FAILURE
                """;
        Map<String, String> methodToRule = Map.of("ADR_001_no_cycles", "no_cycles");

        // when
        List<TestsRunner.VerifyResult> results = runner.parseMavenOutput(readerOf(output), methodToRule);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).ruleName()).isEqualTo("no_cycles");
        assertThat(results.get(0).passed()).isFalse();
        assertThat(results.get(0).message()).contains("Architecture Violation");
    }

    @Test
    void parseMavenOutput_multipleFailures_returnsAllFailed() throws IOException {
        // given
        String output = """
                [ERROR] ADR_001_no_deps  Time elapsed: 0.01 s  <<< FAILURE!
                AssertionError: Dependency rule violated
                [ERROR] ADR_001_no_cycles  Time elapsed: 0.02 s  <<< FAILURE!
                AssertionError: Cycle detected
                """;
        Map<String, String> methodToRule = Map.of(
                "ADR_001_no_deps", "no_deps",
                "ADR_001_no_cycles", "no_cycles"
        );

        // when
        List<TestsRunner.VerifyResult> results = runner.parseMavenOutput(readerOf(output), methodToRule);

        // then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(result -> !result.passed());
    }

    @Test
    void parseMavenOutput_noFailures_returnsEmptyList() throws IOException {
        // given
        String output = "[INFO] Tests run: 2, Failures: 0, Errors: 0\n[INFO] BUILD SUCCESS\n";

        // when
        List<TestsRunner.VerifyResult> results = runner.parseMavenOutput(readerOf(output), Map.of());

        // then
        assertThat(results).isEmpty();
    }

    @Test
    void extractMethodName_gradleArrowFormat_extractsLastSegment() {
        // given / when / then
        assertThat(runner.extractMethodName("ArchitectureFrom_ADR_001Test > ADR_001_no_deps"))
                .isEqualTo("ADR_001_no_deps");
    }

    @Test
    void extractMethodName_fullyQualifiedWithParens_stripsClassAndParens() {
        // given / when / then
        assertThat(runner.extractMethodName("com.example.MyTest.someMethod()"))
                .isEqualTo("someMethod");
    }

    @Test
    void extractMethodName_parameterizedWithBrackets_stripsBrackets() {
        // given / when / then
        assertThat(runner.extractMethodName("someMethod[1]"))
                .isEqualTo("someMethod");
    }

    @Test
    void extractMethodName_simpleName_returnsAsIs() {
        // given / when / then
        assertThat(runner.extractMethodName("  ADR_001_no_deps  "))
                .isEqualTo("ADR_001_no_deps");
    }
}
