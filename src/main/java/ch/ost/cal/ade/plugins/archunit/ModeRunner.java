package ch.ost.cal.ade.plugins.archunit;

import ch.ost.cal.ade.plugins.archunit.compile.Renderer;
import ch.ost.cal.ade.plugins.archunit.compile.RuleTranslator;
import ch.ost.cal.ade.plugins.archunit.data.ArchUnitTestsCollection;
import ch.ost.cal.ade.plugins.archunit.exceptions.BuildException;
import ch.ost.cal.ade.plugins.archunit.spec.Spec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class ModeRunner {

    private static final String CONFIG_OUTPUT_DIR = "output-dir";

    protected final Spec spec;
    protected ArchUnitTestsCollection testsCollection;

    protected ModeRunner(Spec spec) {
        this.spec = spec;
    }

    public final void run() throws BuildException, IOException {
        testsCollection = new RuleTranslator(spec).translate();

        if (!testsCollection.hasArchTests()) {
            System.err.println("warn: no code rules for ADR [" + spec.getAdr().getTitle() + "], skipping");
            return;
        }

        byte[] content = new Renderer().render(testsCollection);
        Path genPath = writeFile(content);
        execute(genPath);
    }

    protected abstract void execute(Path genPath) throws BuildException, IOException;

    protected Path writeFile(byte[] content) throws IOException {
        Path dir = Paths.get(spec.getPluginConfigMap().getOrDefault(CONFIG_OUTPUT_DIR, "."));
        Files.createDirectories(dir);
        Path outPath = dir.resolve(testsCollection.getFileName());
        Files.write(outPath, content);
        return outPath;
    }
}
