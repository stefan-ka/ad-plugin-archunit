package ch.ost.cal.ade.plugins.archunit.compile;

import ch.ost.cal.ade.plugins.archunit.spec.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PredicatesTest {


    @Test
    void toPackagePattern_addsDoubleDotSuffix() {
        // given
        String plain = "com.example.service";

        // when
        String result = Predicates.toPackagePattern(plain);

        // then
        assertThat(result).isEqualTo("com.example.service..");
    }

    @Test
    void toPackagePattern_doesNotModifyPatternThatAlreadyEndsWithDoubleDot() {
        // given / when / then
        assertThat(Predicates.toPackagePattern("com.example..")).isEqualTo("com.example..");
    }

    @Test
    void toPackagePattern_doesNotModifyPatternWithWildcard() {
        // given / when / then
        assertThat(Predicates.toPackagePattern("com.example.*")).isEqualTo("com.example.*");
    }

    @Test
    void toPackagePattern_doesNotModifyPatternWithParens() {
        // given / when / then
        assertThat(Predicates.toPackagePattern("com.example(.*)")).isEqualTo("com.example(.*)");
    }


    @Test
    void normalizePackage_stripsLeadingAndTrailingWhitespace() {
        // given
        String padded = "  com.example  ";

        // when
        String result = Predicates.normalizePackage(padded);

        // then
        assertThat(result).isEqualTo("com.example");
    }

    @Test
    void normalizePackage_stripsTrailingDots() {
        // given
        String withDots = "com.example...";

        // when
        String result = Predicates.normalizePackage(withDots);

        // then
        assertThat(result).isEqualTo("com.example");
    }

    @Test
    void normalizePackage_stripsWhitespaceAndDotsTogether() {
        // given / when / then
        assertThat(Predicates.normalizePackage("  com.example.  ")).isEqualTo("com.example");
    }


    @Test
    void escapeJavaString_escapesDoubleQuote() {
        // given
        String raw = "com.\"example\"";

        // when
        String result = Predicates.escapeJavaString(raw);

        // then
        assertThat(result).isEqualTo("com.\\\"example\\\"");
    }

    @Test
    void escapeJavaString_escapesBackslash() {
        // given
        String raw = "a\\b";

        // when
        String result = Predicates.escapeJavaString(raw);

        // then
        assertThat(result).isEqualTo("a\\\\b");
    }

    @Test
    void escapeJavaString_plainStringIsUnchanged() {
        // given / when / then
        assertThat(Predicates.escapeJavaString("com.example.service")).isEqualTo("com.example.service");
    }


    @Test
    void buildPredicatesChain_singlePredicate_isIndentedWithLeadingDot() {
        // given
        String primary = "resideInAPackage(\"com.example..\")";

        // when
        String chain = Predicates.buildPredicatesChain(primary, List.of());

        // then
        assertThat(chain).isEqualTo("            .resideInAPackage(\"com.example..\")");
    }

    @Test
    void buildPredicatesChain_withOneExclude_appendsAndClause() {
        // given
        String primary = "resideInAPackage(\"com.example..\")";
        List<String> excludes = List.of("doNotHaveSimpleName(\"Stub\")");

        // when
        String chain = Predicates.buildPredicatesChain(primary, excludes);

        // then
        assertThat(chain)
                .contains(".resideInAPackage(\"com.example..\")")
                .contains(".and().doNotHaveSimpleName(\"Stub\")");
    }

    @Test
    void buildPredicatesChain_emptyPrimary_returnsEmptyString() {
        // given / when / then
        assertThat(Predicates.buildPredicatesChain("", List.of())).isEmpty();
    }

    @Test
    void buildPredicatesChain_hasNoTrailingNewline() {
        // given
        String primary = "resideInAPackage(\"com.example..\")";

        // when
        String chain = Predicates.buildPredicatesChain(primary, List.of());

        // then
        assertThat(chain).doesNotEndWith("\n");
    }


    @Test
    void buildExcludeChain_classExclusion_producesDoNotHaveSimpleName() {
        // given
        Exclusion ex = Exclusion.newBuilder()
                .setKind(ExcludeKind.EXCLUDE_CLASS).setValue("AbstractBase").build();

        // when
        List<String> chain = Predicates.buildExcludeChain(List.of(ex));

        // then
        assertThat(chain).containsExactly("doNotHaveSimpleName(\"AbstractBase\")");
    }

    @Test
    void buildExcludeChain_interfaceExclusion_producesDoNotImplement() {
        // given
        Exclusion ex = Exclusion.newBuilder()
                .setKind(ExcludeKind.EXCLUDE_IMPLEMENT_INTERFACE).setValue("com.example.Marker").build();

        // when
        List<String> chain = Predicates.buildExcludeChain(List.of(ex));

        // then
        assertThat(chain).containsExactly("doNotImplement(\"com.example.Marker\")");
    }

    @Test
    void buildExcludeChain_componentExclusion_appendsDoubleDot() {
        // given
        Exclusion ex = Exclusion.newBuilder()
                .setKind(ExcludeKind.EXCLUDE_COMPONENT).setValue("com.example.util").build();

        // when
        List<String> chain = Predicates.buildExcludeChain(List.of(ex));

        // then
        assertThat(chain).containsExactly("doNotResideInPackage(\"com.example.util..\")");
    }

    @Test
    void buildExcludeChain_emptyList_returnsEmptyList() {
        // given / when / then
        assertThat(Predicates.buildExcludeChain(List.of())).isEmpty();
    }


    @Test
    void buildSinglePredicate_namedSelector_resolvesViaSelMap() {
        // given
        Selector sel = Selector.newBuilder()
                .setName("Domain").setPattern("com.example.domain")
                .setKind(SelectorKind.SELECTOR_COMPONENT).build();
        TargetRef ref = TargetRef.newBuilder().setIsInline(false).setValue("Domain").build();

        // when
        String predicate =Predicates.buildSinglePredicate(ref, Map.of("Domain", sel));

        // then
        assertThat(predicate).isEqualTo("resideInAPackage(\"com.example.domain..\")");
    }

    @Test
    void buildSinglePredicate_inlineComponent_addsDoubleDotSuffix() {
        // given
        TargetRef ref = TargetRef.newBuilder()
                .setIsInline(true).setKind(SelectorKind.SELECTOR_COMPONENT)
                .setValue("com.example.infra").build();

        // when
        String predicate =Predicates.buildSinglePredicate(ref, Map.of());

        // then
        assertThat(predicate).isEqualTo("resideInAPackage(\"com.example.infra..\")");
    }

    @Test
    void buildSinglePredicate_inlineClass_usesHaveSimpleName() {
        // given
        TargetRef ref = TargetRef.newBuilder()
                .setIsInline(true).setKind(SelectorKind.SELECTOR_CLASS)
                .setValue("MyService").build();

        // when
        String predicate =Predicates.buildSinglePredicate(ref, Map.of());

        // then
        assertThat(predicate).isEqualTo("haveSimpleName(\"MyService\")");
    }
}
