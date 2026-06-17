package ch.ost.cal.ade.plugins.archunit;

import ch.ost.cal.ade.plugins.archunit.compile.Compiler;
import ch.ost.cal.ade.plugins.archunit.exceptions.BuildException;
import ch.ost.cal.ade.plugins.archunit.spec.Spec;
import ch.ost.cal.ade.plugins.archunit.verify.Verifier;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ArchUnitPlugin {

    private static final String[] MODES = {"compile", "verify"};
    private static final String CONFIG_PREFIX = "archunit";
    private static final String VERSION = "0.1.0";

    public int run(String[] args) {
        if (isInfoRequest(args)) {
            System.out.println(buildInfoJson());
            return 0;
        }
        if (isInteractiveTerminal()) {
            System.err.println("Usage: pipe an ADE Spec protobuf message to stdin");
            System.err.println("       plugin --info");
            return 0;
        }

        Spec spec;
        try {
            spec = readSpec();
        } catch (Exception e) {
            System.err.println("error: cannot read Spec from stdin: " + e.getMessage());
            return 1;
        }

        warnAboutSkippedRules(spec);

        try {
            switch (spec.getMode()) {
                case MODE_VERIFY -> new Verifier(spec).run();
                default -> new Compiler(spec).run();
            }
            return 0;
        } catch (BuildException | IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("error: I/O failure: " + e.getMessage());
            return 1;
        }
    }

    private boolean isInfoRequest(String[] args) {
        return args.length == 1 && "--info".equals(args[0]);
    }

    private boolean isInteractiveTerminal() {
        return System.console() != null;
    }

    private Spec readSpec() throws IOException {
        return Spec.parseFrom(System.in.readAllBytes());
    }

    private void warnAboutSkippedRules(Spec spec) {
        long skipped = spec.getRulesList().stream()
                .filter(rule -> rule.getIsFileRule() || rule.getIsCustomRule())
                .count();
        if (skipped > 0) {
            System.err.println("warn: " + skipped + " rule(s) skipped (plugin can only handle code rules)");
        }
    }

    private String buildInfoJson() {
        String modes = Arrays.stream(MODES)
                .map(mode -> "\"" + mode + "\"")
                .collect(Collectors.joining(","));
        return "{\"modes\":[" + modes + "],"
                + "\"config_prefix\":\"" + CONFIG_PREFIX + "\","
                + "\"version\":\"" + VERSION + "\"}";
    }
}
