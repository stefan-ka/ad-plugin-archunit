package ch.ost.cal.ade.plugins.archunit.compile;

import ch.ost.cal.ade.plugins.archunit.spec.*;
import ch.ost.cal.ade.plugins.archunit.data.ArchUnitTestsCollection;
import ch.ost.cal.ade.plugins.archunit.data.ArchUnitTestData;
import ch.ost.cal.ade.plugins.archunit.exceptions.BuildException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleTranslatorTest {


    private Spec.Builder specBuilder(String adrId, String adrTitle) {
        return Spec.newBuilder()
                .setMode(InvocationMode.MODE_COMPILE)
                .setAdr(Adr.newBuilder().setId(adrId).setTitle(adrTitle).build());
    }

    private Selector component(String name, String pattern) {
        return Selector.newBuilder()
                .setName(name).setPattern(pattern).setKind(SelectorKind.SELECTOR_COMPONENT).build();
    }

    private TargetRef selRef(String selectorName) {
        return TargetRef.newBuilder().setIsInline(false).setValue(selectorName).build();
    }


    @Test
    void translate_adrClassName_sanitizesHyphens() throws BuildException {
        // given
        Spec spec = specBuilder("ADR-001", "Test").build();

        // when
        ArchUnitTestsCollection testsCollection = new RuleTranslator(spec).translate();

        // then
        assertThat(testsCollection.getAdrClassName()).isEqualTo("ArchitectureFrom_ADR_001Test");
        assertThat(testsCollection.getFileName()).isEqualTo("ArchitectureFrom_ADR_001Test.java");
    }

    @Test
    void translate_adrClassName_handlesNumericId() throws BuildException {
        // given
        Spec spec = specBuilder("0001", "Test").build();

        // when
        ArchUnitTestsCollection testsCollection = new RuleTranslator(spec).translate();

        // then
        assertThat(testsCollection.getAdrClassName()).isEqualTo("ArchitectureFrom_0001Test");
    }


    @Test
    void translate_notDepend_generatesNoClassesForbidTest() throws BuildException {
        // given
        Spec spec = specBuilder("ADR-001", "Layered Arch")
                .addSelectors(component("Web", "com.example.web"))
                .addSelectors(component("Domain", "com.example.domain"))
                .addRules(Rule.newBuilder()
                        .setName("rule1").setKind(RuleKind.RULE_NOT_DEPEND)
                        .setFrom(selRef("Web")).addTargets(selRef("Domain"))
                        .setSeverity(Severity.SEVERITY_ERROR)
                        .build())
                .build();

        // when
        ArchUnitTestsCollection testsCollection = new RuleTranslator(spec).translate();

        // then
        assertThat(testsCollection.getArchTests()).hasSize(1);
        ArchUnitTestData testData = testsCollection.getArchTests().get(0);
        assertThat(testData.getTestMethodName()).isEqualTo("ADR_001_rule1");
        assertThat(testData.getClassesExpr()).isEqualTo("noClasses()");
        assertThat(testData.getPredicatesChain()).contains("resideInAPackage(\"com.example.web..\")");
        assertThat(testData.getConditionChain())
                .isEqualTo("dependOnClassesThat().resideInAnyPackage(\"com.example.domain..\")");
        assertThat(testData.isWarning()).isFalse();
    }

    @Test
    void translate_notDepend_multipleTargets_allAppearInCondition() throws BuildException {
        // given
        Spec spec = specBuilder("ADR-001", "Test")
                .addSelectors(component("App", "com.example.app"))
                .addSelectors(component("Web", "com.example.web"))
                .addSelectors(component("Domain", "com.example.domain"))
                .addRules(Rule.newBuilder()
                        .setName("no_ui").setKind(RuleKind.RULE_NOT_DEPEND)
                        .setFrom(selRef("App"))
                        .addTargets(selRef("Web")).addTargets(selRef("Domain"))
                        .setSeverity(Severity.SEVERITY_ERROR)
                        .build())
                .build();

        // when
        ArchUnitTestData testData = new RuleTranslator(spec).translate().getArchTests().get(0);

        // then
        assertThat(testData.getConditionChain())
                .contains("com.example.domain..")
                .contains("com.example.web..");
    }


    @Test
    void translate_dependOnly_invertsAllowedSet() throws BuildException {
        // given — Web may only depend on Service, so Domain becomes forbidden
        Spec spec = specBuilder("ADR-001", "Test")
                .addSelectors(component("Web", "com.example.web"))
                .addSelectors(component("Service", "com.example.service"))
                .addSelectors(component("Domain", "com.example.domain"))
                .addRules(Rule.newBuilder()
                        .setName("web_only_service").setKind(RuleKind.RULE_DEPEND_ONLY)
                        .setFrom(selRef("Web")).addTargets(selRef("Service"))
                        .setSeverity(Severity.SEVERITY_ERROR)
                        .build())
                .build();

        // when
        ArchUnitTestData testData = new RuleTranslator(spec).translate().getArchTests().get(0);

        // then
        assertThat(testData.getClassesExpr()).isEqualTo("noClasses()");
        assertThat(testData.getConditionChain()).contains("com.example.domain..");
        assertThat(testData.getConditionChain())
                .as("the allowed selector must not become forbidden")
                .doesNotContain("com.example.service..");
        assertThat(testData.getConditionChain())
                .as("the subject itself must not become forbidden")
                .doesNotContain("com.example.web..");
    }


    @Test
    void translate_warningSeverity_setsIsWarning() throws BuildException {
        // given
        Spec spec = specBuilder("ADR-001", "Test")
                .addSelectors(component("Web", "com.example.web"))
                .addSelectors(component("Domain", "com.example.domain"))
                .addRules(Rule.newBuilder()
                        .setName("rule1").setKind(RuleKind.RULE_NOT_DEPEND)
                        .setFrom(selRef("Web")).addTargets(selRef("Domain"))
                        .setSeverity(Severity.SEVERITY_WARNING)
                        .build())
                .build();

        // when
        ArchUnitTestData testData = new RuleTranslator(spec).translate().getArchTests().get(0);

        // then
        assertThat(testData.isWarning()).isTrue();
    }


    @Test
    void translate_annotate_producesBeAnnotatedWith() throws BuildException {
        // given
        Spec spec = specBuilder("ADR-001", "Test")
                .addSelectors(component("Service", "com.example.service"))
                .addRules(Rule.newBuilder()
                        .setName("annotated").setKind(RuleKind.RULE_ANNOTATE)
                        .setFrom(selRef("Service"))
                        .addTargets(TargetRef.newBuilder().setIsInline(true)
                                .setValue("org.springframework.stereotype.Service").build())
                        .setSeverity(Severity.SEVERITY_ERROR)
                        .build())
                .build();

        // when
        ArchUnitTestData testData = new RuleTranslator(spec).translate().getArchTests().get(0);

        // then
        assertThat(testData.getClassesExpr()).isEqualTo("classes()");
        assertThat(testData.getConditionChain())
                .isEqualTo("beAnnotatedWith(\"org.springframework.stereotype.Service\")");
    }

    @Test
    void translate_notAnnotate_producesNotBeAnnotatedWith() throws BuildException {
        // given
        Spec spec = specBuilder("ADR-001", "Test")
                .addSelectors(component("Domain", "com.example.domain"))
                .addRules(Rule.newBuilder()
                        .setName("no_spring").setKind(RuleKind.RULE_NOT_ANNOTATE)
                        .setFrom(selRef("Domain"))
                        .addTargets(TargetRef.newBuilder().setIsInline(true)
                                .setValue("org.springframework.stereotype.Service").build())
                        .setSeverity(Severity.SEVERITY_ERROR)
                        .build())
                .build();

        // when
        ArchUnitTestData testData = new RuleTranslator(spec).translate().getArchTests().get(0);

        // then
        assertThat(testData.getConditionChain()).startsWith("notBeAnnotatedWith(");
    }


    @Test
    void translate_visibilityPublic_producesBePublic() throws BuildException {
        // given
        Spec spec = specBuilder("ADR-001", "Test")
                .addSelectors(component("Service", "com.example.service"))
                .addRules(Rule.newBuilder()
                        .setName("public_api").setKind(RuleKind.RULE_VISIBILITY)
                        .setFrom(selRef("Service")).setVisibility(Visibility.VISIBILITY_PUBLIC)
                        .setSeverity(Severity.SEVERITY_ERROR)
                        .build())
                .build();

        // when
        ArchUnitTestData testData = new RuleTranslator(spec).translate().getArchTests().get(0);

        // then
        assertThat(testData.getClassesExpr()).isEqualTo("classes()");
        assertThat(testData.getConditionChain()).isEqualTo("bePublic()");
    }

    @Test
    void translate_visibilityInternal_producesBePackagePrivate() throws BuildException {
        // given
        Spec spec = specBuilder("ADR-001", "Test")
                .addSelectors(component("Service", "com.example.service"))
                .addRules(Rule.newBuilder()
                        .setName("pkg_private").setKind(RuleKind.RULE_VISIBILITY)
                        .setFrom(selRef("Service")).setVisibility(Visibility.VISIBILITY_INTERNAL)
                        .setSeverity(Severity.SEVERITY_ERROR)
                        .build())
                .build();

        // when
        ArchUnitTestData testData = new RuleTranslator(spec).translate().getArchTests().get(0);

        // then
        assertThat(testData.getConditionChain()).isEqualTo("bePackagePrivate()");
    }


    @Test
    void translate_typeConstraintAbstract_producesBeAbstract() throws BuildException {
        // given
        Spec spec = specBuilder("ADR-001", "Test")
                .addSelectors(component("Base", "com.example.base"))
                .addRules(Rule.newBuilder()
                        .setName("abstract_base").setKind(RuleKind.RULE_TYPE_CONSTRAINT)
                        .setFrom(selRef("Base")).setTypeConstraint(TypeConstraint.TYPE_CONSTRAINT_ABSTRACT)
                        .setSeverity(Severity.SEVERITY_ERROR)
                        .build())
                .build();

        // when
        ArchUnitTestData testData = new RuleTranslator(spec).translate().getArchTests().get(0);

        // then
        assertThat(testData.getConditionChain()).isEqualTo("beAbstract()");
    }

    @Test
    void translate_typeConstraintSealed_producesBeFinal() throws BuildException {
        // given
        Spec spec = specBuilder("ADR-001", "Test")
                .addSelectors(component("Val", "com.example.value"))
                .addRules(Rule.newBuilder()
                        .setName("sealed").setKind(RuleKind.RULE_TYPE_CONSTRAINT)
                        .setFrom(selRef("Val")).setTypeConstraint(TypeConstraint.TYPE_CONSTRAINT_SEALED)
                        .setSeverity(Severity.SEVERITY_ERROR)
                        .build())
                .build();

        // when
        ArchUnitTestData testData = new RuleTranslator(spec).translate().getArchTests().get(0);

        // then
        assertThat(testData.getConditionChain()).isEqualTo("beFinal()");
    }


    @Test
    void translate_acyclicRule_generatesSlicesTest() throws BuildException {
        // given
        Spec spec = specBuilder("ADR-001", "Test")
                .addSelectors(component("App", "com.example"))
                .addRules(Rule.newBuilder()
                        .setName("no_cycles").setKind(RuleKind.RULE_ACYCLIC)
                        .setFrom(selRef("App"))
                        .setSeverity(Severity.SEVERITY_ERROR)
                        .build())
                .build();

        // when
        ArchUnitTestsCollection testsCollection = new RuleTranslator(spec).translate();

        // then
        assertThat(testsCollection.getArchTests()).hasSize(1);
        ArchUnitTestData testData = testsCollection.getArchTests().get(0);
        assertThat(testData.isSlices()).isTrue();
        assertThat(testData.getPredicatesChain()).isEqualTo("\"com.example.(*)..\"");
        assertThat(testData.getConditionChain()).isEqualTo("beFreeOfCycles()");
        assertThat(testsCollection.getSkippedRules()).isEmpty();
    }

    @Test
    void translate_accessedByRule_isAddedToSkippedNotToTests() throws BuildException {
        // given
        Spec spec = specBuilder("ADR-001", "Test")
                .addRules(Rule.newBuilder().setName("only_from_web").setKind(RuleKind.RULE_ACCESSED_BY).build())
                .build();

        // when
        ArchUnitTestsCollection testsCollection = new RuleTranslator(spec).translate();

        // then
        assertThat(testsCollection.getSkippedRules())
                .hasSize(1)
                .first().asString().contains("only_from_web").contains("ACCESSED_BY");
    }


    @Test
    void translate_fileRule_isSilentlyIgnoredEntirely() throws BuildException {
        // given
        Spec spec = specBuilder("ADR-001", "Test")
                .addRules(Rule.newBuilder().setName("readme_exists").setIsFileRule(true).build())
                .build();

        // when
        ArchUnitTestsCollection testsCollection = new RuleTranslator(spec).translate();

        // then
        assertThat(testsCollection.getArchTests()).isEmpty();
        assertThat(testsCollection.getSkippedRules())
                .as("file rules must not appear in skipped comments either")
                .isEmpty();
    }


    @Test
    void translate_hasSkippedFlag_setWhenSkippedRulesPresent() throws BuildException {
        // given
        Spec spec = specBuilder("ADR-001", "Test")
                .addSelectors(component("Web", "com.example.web"))
                .addSelectors(component("Domain", "com.example.domain"))
                .addRules(Rule.newBuilder().setName("only_from_web").setKind(RuleKind.RULE_ACCESSED_BY).build())
                .addRules(Rule.newBuilder()
                        .setName("no_dep").setKind(RuleKind.RULE_NOT_DEPEND)
                        .setFrom(selRef("Web")).addTargets(selRef("Domain"))
                        .setSeverity(Severity.SEVERITY_ERROR).build())
                .build();

        // when
        ArchUnitTestsCollection testsCollection = new RuleTranslator(spec).translate();

        // then
        assertThat(testsCollection.hasArchTests()).isTrue();
        assertThat(testsCollection.hasSkipped()).isTrue();
    }


    @Test
    void translate_unresolvableSubject_throwsBuildException() {
        // given — "Web" selector is not registered in the spec
        Spec spec = specBuilder("ADR-001", "Test")
                .addRules(Rule.newBuilder()
                        .setName("bad_rule").setKind(RuleKind.RULE_NOT_DEPEND)
                        .setFrom(selRef("Web")).addTargets(selRef("Domain"))
                        .build())
                .build();

        // when / then
        assertThatThrownBy(() -> new RuleTranslator(spec).translate())
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("bad_rule");
    }


    @Test
    void toIdent_replacesNonIdentifierCharsWithUnderscore() {
        // given / when / then
        assertThat(RuleTranslator.toIdent("my-rule.name")).isEqualTo("my_rule_name");
    }

    @Test
    void toIdent_prefixesDigitStartWithR() {
        // given / when / then
        assertThat(RuleTranslator.toIdent("0001_no_deps")).isEqualTo("R_0001_no_deps");
    }

    @Test
    void toIdent_stripsLeadingAndTrailingUnderscores() {
        // given / when / then
        assertThat(RuleTranslator.toIdent("-rule-")).isEqualTo("rule");
    }
}
