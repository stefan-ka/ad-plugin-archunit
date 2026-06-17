package ch.ost.cal.ade.plugins.archunit.compile;

import ch.ost.cal.ade.plugins.archunit.spec.Exclusion;
import ch.ost.cal.ade.plugins.archunit.spec.Selector;
import ch.ost.cal.ade.plugins.archunit.spec.TargetRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Predicates {

    // 12 spaces: aligns predicate chain with the .that() call depth in the generated test body
    private static final String PREDICATE_INDENT = "            ";

    public static String buildPredicatesChain(String primary, List<String> excludes) {
        List<String> parts = new ArrayList<>();
        if (primary != null && !primary.isBlank()) {
            parts.add(primary);
        }
        parts.addAll(excludes);

        if (parts.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append(PREDICATE_INDENT).append(".").append(parts.get(0)).append("\n");
        for (int i = 1; i < parts.size(); i++) {
            sb.append(PREDICATE_INDENT).append(".and().").append(parts.get(i)).append("\n");
        }
        // strip trailing newline — the template adds its own
        String result = sb.toString();
        while (result.endsWith("\n")) result = result.substring(0, result.length() - 1);
        return result;
    }

    public static String buildSubjectPredicate(TargetRef from, Map<String, Selector> selMap) {
        if (from == null)
            return "";

        List<String> parts = new ArrayList<>();

        String subjectFilter = buildSinglePredicate(from, selMap);
        if (subjectFilter != null && !subjectFilter.isBlank()) {
            parts.add(subjectFilter);
        }

        if (from.hasScope()) {
            String scopeNamespace = resolveTarget(from.getScope(), selMap);
            if (scopeNamespace != null && !scopeNamespace.isBlank()) {
                parts.add(String.format("resideInAPackage(\"%s\")", toPackagePattern(scopeNamespace)));
            }
        }

        if (parts.isEmpty())
            return "";
        return String.join("\n" + PREDICATE_INDENT + ".and().", parts);
    }

    public static String buildSinglePredicate(TargetRef ref, Map<String, Selector> selMap) {
        if (ref == null)
            return "";

        if (!ref.getIsInline()) {
            String namespace = resolveTarget(ref, selMap);
            if (namespace != null && !namespace.isBlank()) {
                return String.format("resideInAPackage(\"%s\")", toPackagePattern(namespace));
            }
            return "";
        }

        switch (ref.getKind()) {
            case SELECTOR_COMPONENT: {
                String namespace = normalizePackage(ref.getValue());
                if (namespace.isBlank())
                    return "";
                if (ref.getIsMatch()) {
                    // IsMatch for a component means the value is already a pattern
                    return String.format("resideInAPackage(\"%s\")", escapeJavaString(namespace));
                }
                return String.format("resideInAPackage(\"%s\")", toPackagePattern(namespace));
            }
            case SELECTOR_CLASS:
            case SELECTOR_INTERFACE: {
                if (ref.getValue().isBlank())
                    return "";
                if (ref.getIsMatch()) {
                    return String.format("haveNameMatching(\"%s\")", escapeJavaString(ref.getValue()));
                }
                return String.format("haveSimpleName(\"%s\")", escapeJavaString(ref.getValue()));
            }
            default: {
                if (!ref.getValue().isBlank()) {
                    return String.format("resideInAPackage(\"%s\")",
                            toPackagePattern(normalizePackage(ref.getValue())));
                }
                return "";
            }
        }
    }

    public static List<String> buildExcludeChain(List<Exclusion> excludes) {
        List<String> out = new ArrayList<>();
        for (Exclusion exclusion : excludes) {
            switch (exclusion.getKind()) {
                case EXCLUDE_CLASS:
                    out.add(String.format("doNotHaveSimpleName(\"%s\")", escapeJavaString(exclusion.getValue())));
                    break;
                case EXCLUDE_IMPLEMENT_INTERFACE:
                    out.add(String.format("doNotImplement(\"%s\")", escapeJavaString(exclusion.getValue())));
                    break;
                case EXCLUDE_COMPONENT:
                    String namespace = normalizePackage(exclusion.getValue());
                    out.add(String.format("doNotResideInPackage(\"%s\")", toPackagePattern(escapeJavaString(namespace))));
                    break;
                default:
                    break;
            }
        }
        return out;
    }

    public static String resolveTarget(TargetRef target, Map<String, Selector> selectorMap) {
        if (target == null)
            return "";
        if (target.getIsInline()) {
            return normalizePackage(target.getValue());
        }
        Selector sel = selectorMap.get(target.getValue());
        if (sel != null) {
            return normalizePackage(sel.getPattern());
        }
        return "";
    }

    public static String toPackagePattern(String packageName) {
        if (packageName.endsWith("..") || packageName.contains("*") || packageName.contains("("))
            return packageName;
        return packageName + "..";
    }

    public static String normalizePackage(String value) {
        value = value.trim();
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value.trim();
    }

    public static String escapeJavaString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
