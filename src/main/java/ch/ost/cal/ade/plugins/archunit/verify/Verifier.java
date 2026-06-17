package ch.ost.cal.ade.plugins.archunit.verify;

import ch.ost.cal.ade.plugins.archunit.ModeRunner;
import ch.ost.cal.ade.plugins.archunit.exceptions.BuildException;
import ch.ost.cal.ade.plugins.archunit.spec.Spec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.deleteIfExists;

public class Verifier extends ModeRunner {

    public Verifier(Spec spec) {
        super(spec);
    }

    @Override
    protected void execute(Path genPath) throws BuildException, IOException {
        try {
            List<TestsRunner.VerifyResult> results = new TestsRunner(spec.getPluginConfigMap())
                    .verify(testsCollection);
            reportResults(results);
        } finally {
            tryDelete(genPath);
        }
    }

    void reportResults(List<TestsRunner.VerifyResult> results) throws BuildException {
        boolean hasFailures = false;
        for (TestsRunner.VerifyResult result : results) {
            if (result.passed()) {
                System.err.println("passed [" + result.ruleName() + "]");
            } else {
                String detail = result.message().isEmpty() ? "" : ": " + result.message();
                System.err.println("error: failed [" + result.ruleName() + "]" + detail);
                hasFailures = true;
            }
        }
        if (hasFailures) {
            throw new BuildException("one or more architecture rules failed");
        }
    }

    private void tryDelete(Path path) {
        try {
            deleteIfExists(path);
        } catch (IOException e) {
            System.err.println("warn: could not remove generated file " + path + ": " + e.getMessage());
        }
    }
}
