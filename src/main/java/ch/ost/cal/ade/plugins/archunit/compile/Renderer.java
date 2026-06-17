package ch.ost.cal.ade.plugins.archunit.compile;

import ch.ost.cal.ade.plugins.archunit.ArchUnitPlugin;
import ch.ost.cal.ade.plugins.archunit.data.ArchUnitTestData;
import ch.ost.cal.ade.plugins.archunit.data.ArchUnitTestsCollection;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Renderer {

    private final Configuration freemarkerConfig;

    public Renderer() {
        freemarkerConfig = new Configuration(Configuration.VERSION_2_3_33);
        freemarkerConfig.setClassForTemplateLoading(ArchUnitPlugin.class, "/archunit");
        freemarkerConfig.setDefaultEncoding("UTF-8");
        freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        freemarkerConfig.setLogTemplateExceptions(false);
    }

    public byte[] render(ArchUnitTestsCollection testsCollection) throws IOException {
        try {
            Template tmpl = freemarkerConfig.getTemplate("ArchUnitTest.ftl");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                tmpl.process(buildModel(testsCollection), writer);
            }
            return out.toByteArray();
        } catch (TemplateException e) {
            throw new IOException("template rendering failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildModel(ArchUnitTestsCollection testsCollection) {
        Map<String, Object> model = new HashMap<>();
        model.put("adrId", testsCollection.getAdrId());
        model.put("adrTitle", testsCollection.getAdrTitle());
        model.put("adrClassName", testsCollection.getAdrClassName());
        model.put("hasArchTests", testsCollection.hasArchTests());
        model.put("hasSkipped", testsCollection.hasSkipped());
        model.put("skippedRules", testsCollection.getSkippedRules());
        model.put("importedPackages", testsCollection.getImportedPackages());
        model.put("packageName", testsCollection.getPackageName());
        model.put("archTests", toMaps(testsCollection.getArchTests()));
        return model;
    }

    private List<Map<String, Object>> toMaps(List<ArchUnitTestData> tests) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ArchUnitTestData testData : tests) {
            Map<String, Object> testMap = new HashMap<>();
            testMap.put("testMethodName", testData.getTestMethodName());
            testMap.put("classesExpr", testData.getClassesExpr());
            testMap.put("predicatesChain", testData.getPredicatesChain());
            testMap.put("conditionChain", testData.getConditionChain());
            testMap.put("adrId", testData.getAdrId());
            testMap.put("adrTitle", testData.getAdrTitle());
            testMap.put("ruleName", testData.getRuleName());
            testMap.put("isWarning", testData.isWarning());
            testMap.put("isSlices", testData.isSlices());
            result.add(testMap);
        }
        return result;
    }
}
