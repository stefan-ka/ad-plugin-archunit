package ch.ost.cal.ade.plugins.archunit.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArchUnitTestsCollection {
    private final String adrId;
    private final String adrTitle;
    private final String adrClassName;
    private final boolean hasArchTests;
    private final boolean hasSkipped;
    private final List<ArchUnitTestData> archTests;
    private final List<String> skippedRules;
    private final String importedPackages;
    private final String packageName;

    private ArchUnitTestsCollection(final Builder builder) {
        this.adrId = builder.adrId;
        this.adrTitle = builder.adrTitle;
        this.adrClassName = builder.adrClassName;
        this.hasArchTests = builder.hasArchTests;
        this.hasSkipped = builder.hasSkipped;
        this.archTests = builder.archTests;
        this.skippedRules = builder.skippedRules;
        this.importedPackages = builder.importedPackages;
        this.packageName = builder.packageName;
    }

    public Map<String, String> buildMethodToRuleMap() {
        Map<String, String> methodToRuleMap = new LinkedHashMap<>();
        for (ArchUnitTestData testData : archTests) {
            methodToRuleMap.put(testData.getTestMethodName(), testData.getRuleName());
        }
        return methodToRuleMap;
    }

    public String getFileName() {
        return adrClassName + ".java";
    }

    public String getAdrId() {
        return adrId;
    }

    public String getAdrTitle() {
        return adrTitle;
    }

    public String getAdrClassName() {
        return adrClassName;
    }

    public boolean hasArchTests() {
        return hasArchTests;
    }

    public boolean hasSkipped() {
        return hasSkipped;
    }

    public List<ArchUnitTestData> getArchTests() {
        return archTests;
    }

    public List<String> getSkippedRules() {
        return skippedRules;
    }

    public String getImportedPackages() {
        return importedPackages;
    }

    public String getPackageName() {
        return packageName;
    }

    public static class Builder {
        private final String adrId;
        private String adrTitle;
        private String adrClassName;
        private boolean hasArchTests = false;
        private boolean hasSkipped = false;
        private List<ArchUnitTestData> archTests;
        private List<String> skippedRules;
        private String importedPackages;
        private String packageName;

        public Builder(final String adrId) {
            this.adrId = adrId;
        }

        public Builder withAdrTitle(final String adrTitle) {
            this.adrTitle = adrTitle;
            return this;
        }

        public Builder withAdrClassName(final String adrClassName) {
            this.adrClassName = adrClassName;
            return this;
        }

        public Builder withArchTests(final List<ArchUnitTestData> archTests) {
            this.archTests = archTests;
            this.hasArchTests = !archTests.isEmpty();
            return this;
        }

        public Builder withSkippedRules(final List<String> skippedRules) {
            this.skippedRules = skippedRules;
            this.hasSkipped = !skippedRules.isEmpty();
            return this;
        }

        public Builder withImportedPackages(final String importedPackages) {
            this.importedPackages = importedPackages;
            return this;
        }

        public Builder withPackageName(final String packageName) {
            this.packageName = packageName;
            return this;
        }

        public ArchUnitTestsCollection build() {
            return new ArchUnitTestsCollection(this);
        }
    }
}
