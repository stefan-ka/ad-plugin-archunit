package ch.ost.cal.ade.plugins.archunit.compile;

import ch.ost.cal.ade.plugins.archunit.ModeRunner;
import ch.ost.cal.ade.plugins.archunit.spec.Spec;

import java.nio.file.Path;

public class Compiler extends ModeRunner {

    public Compiler(Spec spec) {
        super(spec);
    }

    @Override
    protected void execute(Path genPath) {
        System.err.println("generated " + genPath.getFileName()
                + " for rules in ADR [" + spec.getAdr().getTitle() + "]");
    }
}
