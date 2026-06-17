package ch.ost.cal.ade.plugins.archunit.verify;

import ch.ost.cal.ade.plugins.archunit.data.ArchUnitTestsCollection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runs the generated test class via Maven or Gradle and parses per-rule outcomes.
 * <p>
 * Required plugin_config key:
 * test-project        path to the Maven pom.xml directory or Gradle project
 * <p>
 * Optional plugin_config keys:
 * archunit.build-tool   "mvn" (default) or "gradle"
 */
public class TestsRunner {

    private static final String CONFIG_TEST_PROJECT = "test-project";
    private static final String CONFIG_BUILD_TOOL = "build-tool";

    public record VerifyResult(String ruleName, boolean passed, String message) {
    }

    private final Map<String, String> config;

    public TestsRunner(Map<String, String> config) {
        this.config = Collections.unmodifiableMap(config);
    }

    public List<VerifyResult> verify(ArchUnitTestsCollection testsCollection) throws IOException {
        String testProject = config.get(CONFIG_TEST_PROJECT);
        if (testProject == null || testProject.isBlank()) {
            throw new IllegalArgumentException(
                    "plugin_config key \"" + CONFIG_TEST_PROJECT + "\" is required for verify mode");
        }

        boolean useGradle = "gradle".equalsIgnoreCase(config.getOrDefault(CONFIG_BUILD_TOOL, "mvn"));
        List<String> command = useGradle
                ? buildGradleCommand(testProject, testsCollection.getAdrClassName())
                : buildMavenCommand(testProject, testsCollection.getAdrClassName());

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

        List<VerifyResult> results;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            results = useGradle
                    ? parseGradleOutput(reader, testsCollection.buildMethodToRuleMap())
                    : parseMavenOutput(reader, testsCollection.buildMethodToRuleMap());
        }

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return results;
    }


    private List<String> buildGradleCommand(String project, String className) {
        return List.of(
                isWindows() ? "gradlew.bat" : "./gradlew",
                "-p", project,
                "test", "--tests", className + "*", "--rerun-tasks"
        );
    }

    private List<String> buildMavenCommand(String project, String className) {
        return List.of(
                "mvn", "-f", project,
                "test",
                "-Dtest=" + className,
                "-Dsurefire.failIfNoSpecifiedTests=false"
        );
    }


    /**
     * Parses Gradle test output lines of the form:
     * ClassName > methodName PASSED
     * ClassName > methodName FAILED
     * AssertionError: ...
     */
    public List<VerifyResult> parseGradleOutput(BufferedReader reader,
                                         Map<String, String> methodToRule) throws IOException {
        List<VerifyResult> results = new ArrayList<>();
        String currentFailed = null;
        List<String> failMsgLines = new ArrayList<>();
        String line;

        while ((line = reader.readLine()) != null) {
            System.err.println(line);
            String trimmed = line.trim();

            if (trimmed.contains(" PASSED")) {
                flushFailure(results, currentFailed, failMsgLines, methodToRule);
                currentFailed = null;
                failMsgLines = new ArrayList<>();
                String method = extractMethodName(trimmed.replace(" PASSED", ""));
                results.add(new VerifyResult(methodToRule.getOrDefault(method, method), true, ""));
                continue;
            }
            if (trimmed.contains(" FAILED") && !trimmed.startsWith("[")) {
                flushFailure(results, currentFailed, failMsgLines, methodToRule);
                failMsgLines = new ArrayList<>();
                currentFailed = extractMethodName(trimmed.replace(" FAILED", ""));
                continue;
            }
            if (currentFailed != null) {
                if (line.startsWith("    ") || line.startsWith("\t")) failMsgLines.add(trimmed);
                else if (!trimmed.isEmpty()) {
                    flushFailure(results, currentFailed, failMsgLines, methodToRule);
                    currentFailed = null;
                    failMsgLines = new ArrayList<>();
                }
            }
        }
        flushFailure(results, currentFailed, failMsgLines, methodToRule);
        return results;
    }

    /**
     * Parses Maven Surefire output lines of the form:
     * [ERROR] testMethod  Time elapsed: 0.1 s  <<< FAILURE!
     * AssertionError: ...
     */
    public List<VerifyResult> parseMavenOutput(BufferedReader reader,
                                        Map<String, String> methodToRule) throws IOException {
        List<VerifyResult> results = new ArrayList<>();
        String currentFailed = null;
        List<String> failMsgLines = new ArrayList<>();
        String line;

        while ((line = reader.readLine()) != null) {
            System.err.println(line);
            String trimmed = line.trim();

            if (trimmed.contains("<<< FAILURE!") || trimmed.contains("<<< ERROR!")) {
                flushFailure(results, currentFailed, failMsgLines, methodToRule);
                failMsgLines = new ArrayList<>();
                String part = trimmed.replaceAll("<<<.*", "").replaceAll("^\\[.*?]\\s*", "")
                        .replaceAll("\\s+Time elapsed:.*", "").trim();
                currentFailed = extractMethodName(part);
                continue;
            }
            if (currentFailed != null) {
                if (!trimmed.isEmpty() && !trimmed.startsWith("[")) failMsgLines.add(trimmed);
                else {
                    flushFailure(results, currentFailed, failMsgLines, methodToRule);
                    currentFailed = null;
                    failMsgLines = new ArrayList<>();
                }
            }
        }
        flushFailure(results, currentFailed, failMsgLines, methodToRule);
        return results;
    }


    private void flushFailure(List<VerifyResult> results, String currentFailed,
                              List<String> failMsgLines, Map<String, String> methodToRule) {
        if (currentFailed == null) return;
        String ruleName = methodToRule.getOrDefault(currentFailed, currentFailed);
        String message = failMsgLines.stream()
                .filter(failLine -> failLine.startsWith("AssertionError:") || failLine.startsWith("Error Message:") || failLine.startsWith("Message:"))
                .map(failLine -> failLine.substring(failLine.indexOf(':') + 1).trim())
                .findFirst()
                .orElse(failMsgLines.isEmpty() ? "" : failMsgLines.get(0));
        results.add(new VerifyResult(ruleName, false, message));
    }

    public String extractMethodName(String rawName) {
        rawName = rawName.trim();

        int arrow = rawName.lastIndexOf(" > ");
        if (arrow >= 0)
            rawName = rawName.substring(arrow + 3).trim();

        int dot = rawName.lastIndexOf('.');
        if (dot >= 0)
            rawName = rawName.substring(dot + 1);

        int paren = rawName.indexOf('(');
        if (paren > 0)
            rawName = rawName.substring(0, paren);

        int bracket = rawName.indexOf('[');
        if (bracket > 0)
            rawName = rawName.substring(0, bracket);
        return rawName.trim();
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
