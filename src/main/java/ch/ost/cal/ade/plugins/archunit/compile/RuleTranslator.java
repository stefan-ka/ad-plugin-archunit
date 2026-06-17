package ch.ost.cal.ade.plugins.archunit.compile;

import ch.ost.cal.ade.plugins.archunit.data.ArchUnitTestData;
import ch.ost.cal.ade.plugins.archunit.data.ArchUnitTestsCollection;
import ch.ost.cal.ade.plugins.archunit.exceptions.BuildException;
import ch.ost.cal.ade.plugins.archunit.spec.*;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ch.ost.cal.ade.plugins.archunit.compile.Predicates.*;
import static ch.ost.cal.ade.plugins.archunit.data.ArchUnitTestsCollection.Builder;

public class RuleTranslator {

    private static final Pattern IDENT_REGEX = Pattern.compile("[^a-zA-Z0-9_]+");
    private static final String CONFIG_IMPORT_PACKAGES = "import-packages";
    private static final String CONFIG_PACKAGE = "generated-tests-package";

    private final Spec spec;
    private final Map<String, Selector> selectorMap;

    public RuleTranslator(Spec spec) {
        this.spec = spec;
        this.selectorMap = buildSelMap(spec);
    }

    public ArchUnitTestsCollection translate() throws BuildException {
        List<ArchUnitTestData> archTests = new ArrayList<>();
        List<String> skippedRules = new ArrayList<>();

        for (Rule rule : spec.getRulesList()) {
            if (rule.getIsFileRule()) continue;

            switch (rule.getKind()) {
                case RULE_UNSPECIFIED ->
                        throw new BuildException("rule \"" + rule.getName() + "\": no dependency constraints defined");
                case RULE_NOT_DEPEND -> archTests.add(buildForbidTest(rule));
                case RULE_DEPEND_ONLY -> archTests.add(buildAllowOnlyTest(rule));
                case RULE_ANNOTATE -> archTests.add(buildAnnotateTest(rule, "beAnnotatedWith"));
                case RULE_NOT_ANNOTATE -> archTests.add(buildAnnotateTest(rule, "notBeAnnotatedWith"));
                case RULE_IMPLEMENT -> archTests.add(buildTypeTargetTest(rule, "implement"));
                case RULE_NOT_IMPLEMENT -> archTests.add(buildTypeTargetTest(rule, "notImplement"));
                case RULE_EXTEND -> archTests.add(buildTypeTargetTest(rule, "beAssignableTo"));
                case RULE_NOT_EXTEND -> archTests.add(buildTypeTargetTest(rule, "notBeAssignableTo"));
                case RULE_IN -> archTests.add(buildPackageCondTest(rule, "resideInAPackage"));
                case RULE_NOT_IN -> archTests.add(buildPackageCondTest(rule, "resideOutsideOfPackage"));
                case RULE_MATCH -> archTests.add(buildNamePatternCondTest(rule, "haveNameMatching"));
                case RULE_NOT_MATCH -> archTests.add(buildNamePatternCondTest(rule, "notHaveNameMatching"));
                case RULE_VISIBILITY -> archTests.add(buildVisibilityTest(rule));
                case RULE_TYPE_CONSTRAINT -> archTests.add(buildTypeConstraintTest(rule));
                case RULE_ACCESSED_BY -> skippedRules.add(rule.getName()
                        + " (ACCESSED_BY: incoming dependency checks are not expressible in ArchUnit)");
                case RULE_ACYCLIC -> archTests.add(buildAcyclicTest(rule));
                default ->
                        throw new BuildException("rule \"" + rule.getName() + "\": unsupported kind " + rule.getKind());
            }
        }

        return new Builder(spec.getAdr().getId())
                .withAdrTitle(spec.getAdr().getTitle())
                .withAdrClassName(toClassName(spec.getAdr().getId()))
                .withArchTests(archTests)
                .withSkippedRules(skippedRules)
                .withImportedPackages(resolveImportedPackages())
                .withPackageName(spec.getPluginConfigMap().getOrDefault(CONFIG_PACKAGE, "generated"))
                .build();
    }


    private ArchUnitTestData buildForbidTest(Rule rule) throws BuildException {
        String subject = requireSubjectPredicate(rule, "forbid");

        List<String> forbidden = rule.getTargetsList().stream()
                .map(targetRef -> resolveTarget(targetRef, selectorMap))
                .filter(namespace -> !namespace.isBlank())
                .toList();
        forbidden = uniqueNonEmpty(forbidden);
        Collections.sort(forbidden);

        if (forbidden.isEmpty()) {
            throw new BuildException("rule \"" + rule.getName() + "\": forbid has no resolvable targets");
        }

        String conditionChain = "dependOnClassesThat().resideInAnyPackage(" + toPackageArgs(forbidden) + ")";
        return newTestCase(rule, "noClasses()", buildPredicatesChain(rule, subject), conditionChain);
    }

    private ArchUnitTestData buildAllowOnlyTest(Rule rule) throws BuildException {
        String subject = requireSubjectPredicate(rule, "allow_only");

        Set<String> allowedSelNames = new HashSet<>();
        for (TargetRef targetRef : rule.getTargetsList()) {
            if (!targetRef.getIsInline())
                allowedSelNames.add(targetRef.getValue());
        }

        // Invert: every selector not in the allowed set becomes forbidden
        List<String> forbidden = new ArrayList<>();
        for (Map.Entry<String, Selector> entry : selectorMap.entrySet()) {
            String name = entry.getKey();

            if (rule.getFrom() != null && !rule.getFrom().getIsInline() && name.equals(rule.getFrom().getValue()))
                continue;

            if (allowedSelNames.contains(name))
                continue;

            String namespace = normalizePackage(entry.getValue().getPattern());

            if (!namespace.isBlank())
                forbidden.add(namespace);
        }
        forbidden = uniqueNonEmpty(forbidden);
        Collections.sort(forbidden);

        if (forbidden.isEmpty()) {
            throw new BuildException("rule \"" + rule.getName()
                    + "\": allow_only would be a no-op (no forbidden packages inferred). "
                    + "Add more selectors or use explicit 'forbid' rules.");
        }

        return newTestCase(rule, "noClasses()", buildPredicatesChain(rule, subject),
                "dependOnClassesThat().resideInAnyPackage(" + toPackageArgs(forbidden) + ")");
    }

    private ArchUnitTestData buildAnnotateTest(Rule rule, String condMethod) throws BuildException {
        requireTargets(rule, "annotate");
        String conditionChain = condMethod + "(\"" + escapeJavaString(rule.getTargetsList().get(0).getValue()) + "\")";
        return newTestCase(rule, "classes()", buildPredicatesChain(rule, requireSubjectPredicate(rule, "annotate")), conditionChain);
    }

    private ArchUnitTestData buildTypeTargetTest(Rule rule, String condMethod) throws BuildException {
        requireTargets(rule, condMethod);
        String conditionChain = condMethod + "(\"" + escapeJavaString(rule.getTargetsList().get(0).getValue()) + "\")";
        return newTestCase(rule, "classes()", buildPredicatesChain(rule, requireSubjectPredicate(rule, condMethod)), conditionChain);
    }

    private ArchUnitTestData buildPackageCondTest(Rule rule, String condMethod) throws BuildException {
        requireTargets(rule, condMethod);
        String namespace = resolveTarget(rule.getTargetsList().get(0), selectorMap);

        if (namespace.isBlank())
            throw new BuildException("rule \"" + rule.getName() + "\": cannot resolve namespace target");

        String conditionChain = condMethod + "(\"" + toPackagePattern(escapeJavaString(namespace)) + "\")";
        return newTestCase(rule, "classes()", buildPredicatesChain(rule, requireSubjectPredicate(rule, condMethod)), conditionChain);
    }

    private ArchUnitTestData buildNamePatternCondTest(Rule rule, String condMethod) throws BuildException {
        requireTargets(rule, condMethod);
        String conditionChain = condMethod + "(\"" + escapeJavaString(rule.getTargetsList().get(0).getValue()) + "\")";
        return newTestCase(rule, "classes()", buildPredicatesChain(rule, requireSubjectPredicate(rule, condMethod)), conditionChain);
    }

    private ArchUnitTestData buildVisibilityTest(Rule rule) throws BuildException {
        String condMethod = switch (rule.getVisibility()) {
            case VISIBILITY_PUBLIC -> "bePublic()";
            case VISIBILITY_INTERNAL -> "bePackagePrivate()";
            case VISIBILITY_PRIVATE -> "bePrivate()";
            default -> throw new BuildException("rule \"" + rule.getName() + "\": unspecified visibility");
        };
        return newTestCase(rule, "classes()", buildPredicatesChain(rule, requireSubjectPredicate(rule, "visibility")), condMethod);
    }

    private ArchUnitTestData buildTypeConstraintTest(Rule rule) throws BuildException {
        String condMethod = switch (rule.getTypeConstraint()) {
            case TYPE_CONSTRAINT_ABSTRACT -> "beAbstract()";
            case TYPE_CONSTRAINT_SEALED -> "beFinal()";
            case TYPE_CONSTRAINT_STATIC -> "haveModifier(com.tngtech.archunit.core.domain.JavaModifier.STATIC)";
            default -> throw new BuildException("rule \"" + rule.getName() + "\": unspecified type constraint");
        };
        return newTestCase(rule, "classes()", buildPredicatesChain(rule, requireSubjectPredicate(rule, "type_constraint")), condMethod);
    }

    private ArchUnitTestData buildAcyclicTest(Rule rule) throws BuildException {
        if (rule.getFrom() == null)
            throw new BuildException("rule \"" + rule.getName() + "\": missing 'from' subject for acyclic rule");

        String namespace = resolveTarget(rule.getFrom(), selectorMap);
        if (namespace.isBlank())
            throw new BuildException("rule \"" + rule.getName() + "\": cannot resolve subject for acyclic rule");

        String slicesPattern = normalizePackage(namespace) + ".(*)..";
        String matchingArg = "\"" + escapeJavaString(slicesPattern) + "\"";
        return new ArchUnitTestData.Builder(toIdent(spec.getAdr().getId() + "_" + rule.getName()))
                .withClassesExpr("slices()")
                .withPredicatesChain(matchingArg)
                .withConditionChain("beFreeOfCycles()")
                .withAdrId(spec.getAdr().getId())
                .withAdrTitle(spec.getAdr().getTitle())
                .withRuleName(rule.getName())
                .withWarning(rule.getSeverity() == Severity.SEVERITY_WARNING)
                .asSlices()
                .build();
    }


    private ArchUnitTestData newTestCase(Rule rule, String classesExpr, String predicatesChain, String conditionChain) {
        return new ArchUnitTestData.Builder(toIdent(spec.getAdr().getId() + "_" + rule.getName()))
                .withClassesExpr(classesExpr)
                .withPredicatesChain(predicatesChain)
                .withConditionChain(conditionChain)
                .withAdrId(spec.getAdr().getId())
                .withAdrTitle(spec.getAdr().getTitle())
                .withRuleName(rule.getName())
                .withWarning(rule.getSeverity() == Severity.SEVERITY_WARNING)
                .build();
    }

    private String buildPredicatesChain(Rule rule, String subjectPredicate) {
        return Predicates.buildPredicatesChain(subjectPredicate, buildExcludeChain(rule.getExcludesList()));
    }

    private String requireSubjectPredicate(Rule rule, String context) throws BuildException {
        if (rule.getFrom() == null)
            throw new BuildException("rule \"" + rule.getName() + "\": missing 'from' subject");
        String predicate = buildSubjectPredicate(rule.getFrom(), selectorMap);
        if (predicate.isBlank())
            throw new BuildException("rule \"" + rule.getName() + "\": cannot resolve subject for " + context + " rule");
        return predicate;
    }

    private void requireTargets(Rule rule, String context) throws BuildException {
        if (rule.getTargetsList().isEmpty())
            throw new BuildException("rule \"" + rule.getName() + "\": " + context + " requires at least one target");
    }

    private String toPackageArgs(List<String> namespaces) {
        return namespaces.stream()
                .map(namespace -> "\"" + toPackagePattern(escapeJavaString(namespace)) + "\"")
                .collect(Collectors.joining(", "));
    }

    private String resolveImportedPackages() {
        String configString = spec.getPluginConfigMap().getOrDefault(CONFIG_IMPORT_PACKAGES, "");
        String packages = Arrays.stream(configString.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(entry -> "\"" + escapeJavaString(entry) + "\"")
                .collect(Collectors.joining(", "));
        return packages.isBlank() ? "\"\"" : packages;
    }

    private static Map<String, Selector> buildSelMap(Spec spec) {
        Map<String, Selector> map = new LinkedHashMap<>();
        spec.getSelectorsList().forEach(selector -> map.put(selector.getName(), selector));
        return map;
    }

    private static String toClassName(String adrId) {
        String sanitized = IDENT_REGEX.matcher(adrId).replaceAll("_").replaceAll("^_+|_+$", "");
        return "ArchitectureFrom_" + (sanitized.isEmpty() ? "UNKNOWN" : sanitized) + "Test";
    }

    static String toIdent(String identifier) {
        identifier = IDENT_REGEX.matcher(identifier).replaceAll("_").replaceAll("^_+|_+$", "");
        if (identifier.isEmpty()) return "Rule";
        if (Character.isDigit(identifier.charAt(0))) identifier = "R_" + identifier;
        return identifier;
    }

    private static List<String> uniqueNonEmpty(List<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = normalizePackage(value);
            if (!normalized.isEmpty() && seen.add(normalized)) result.add(normalized);
        }
        return result;
    }
}
