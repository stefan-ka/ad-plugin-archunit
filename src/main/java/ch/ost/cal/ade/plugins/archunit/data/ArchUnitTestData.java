package ch.ost.cal.ade.plugins.archunit.data;

public class ArchUnitTestData {
    private final String testMethodName;
    private final String classesExpr;      // "classes()", "noClasses()", or "slices()"
    private final String predicatesChain;  // indented .that().<x>.and().<y> lines; or .matching() arg when isSlices
    private final String conditionChain;   // e.g. "dependOnClassesThat().resideInAnyPackage(...)"
    private final String adrId;
    private final String adrTitle;
    private final String ruleName;
    private final boolean isWarning;
    private final boolean isSlices;        // if true, use slices().matching(predicatesChain) instead of classes expr

    private ArchUnitTestData(final Builder builder) {
        this.testMethodName = builder.testMethodName;
        this.classesExpr = builder.classesExpr;
        this.predicatesChain = builder.predicatesChain;
        this.conditionChain = builder.conditionChain;
        this.adrId = builder.adrId;
        this.adrTitle = builder.adrTitle;
        this.ruleName = builder.ruleName;
        this.isWarning = builder.isWarning;
        this.isSlices = builder.isSlices;
    }

    public String getTestMethodName() {
        return testMethodName;
    }

    public String getClassesExpr() {
        return classesExpr;
    }

    public String getPredicatesChain() {
        return predicatesChain;
    }

    public String getConditionChain() {
        return conditionChain;
    }

    public String getAdrId() {
        return adrId;
    }

    public String getAdrTitle() {
        return adrTitle;
    }

    public String getRuleName() {
        return ruleName;
    }

    public boolean isWarning() {
        return isWarning;
    }

    public boolean isSlices() {
        return isSlices;
    }

    public static class Builder {
        private final String testMethodName;
        private String classesExpr;
        private String predicatesChain;
        private String conditionChain;
        private String adrId;
        private String adrTitle;
        private String ruleName;
        private boolean isWarning = false;
        private boolean isSlices = false;

        public Builder(final String testMethodName) {
            this.testMethodName = testMethodName;
        }

        public Builder withClassesExpr(final String classesExpr) {
            this.classesExpr = classesExpr;
            return this;
        }

        public Builder withPredicatesChain(final String predicatesChain) {
            this.predicatesChain = predicatesChain;
            return this;
        }

        public Builder withConditionChain(final String conditionChain) {
            this.conditionChain = conditionChain;
            return this;
        }

        public Builder withAdrId(final String adrId) {
            this.adrId = adrId;
            return this;
        }

        public Builder withAdrTitle(final String adrTitle) {
            this.adrTitle = adrTitle;
            return this;
        }

        public Builder withRuleName(final String ruleName) {
            this.ruleName = ruleName;
            return this;
        }

        public Builder withWarning(final boolean isWarning) {
            this.isWarning = isWarning;
            return this;
        }

        public Builder asSlices() {
            this.isSlices = true;
            return this;
        }

        public ArchUnitTestData build() {
            return new ArchUnitTestData(this);
        }
    }
}
