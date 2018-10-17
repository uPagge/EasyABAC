package custis.easyabac.generation;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.utils.SourceRoot;
import custis.easyabac.api.NotPermittedException;
import custis.easyabac.api.test.EasyAbacBaseTestClass;
import custis.easyabac.api.test.TestDescription;
import custis.easyabac.core.init.EasyAbacInitException;
import custis.easyabac.core.model.abac.AbacAuthModel;
import custis.easyabac.core.model.abac.Effect;
import custis.easyabac.core.model.abac.Policy;
import custis.easyabac.core.model.abac.Rule;
import custis.easyabac.core.model.abac.attribute.Attribute;
import custis.easyabac.core.model.abac.attribute.DataType;
import custis.easyabac.core.model.abac.attribute.Resource;
import custis.easyabac.generation.algorithm.CombinationAlgorithmFactory;
import custis.easyabac.generation.algorithm.FunctionUtils;
import custis.easyabac.generation.algorithm.TestGenerationAlgorithm;
import custis.easyabac.pdp.AuthResponse;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.Parameterized;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static custis.easyabac.generation.ModelGenerator.ACTION_SUFFIX;
import static custis.easyabac.generation.ModelGenerator.resolvePathForSourceFile;
import static custis.easyabac.generation.algorithm.FunctionUtils.ANY_FUNCTION;
import static custis.easyabac.pdp.AuthResponse.Decision.DENY;
import static custis.easyabac.pdp.AuthResponse.Decision.PERMIT;
import static org.apache.commons.lang3.StringUtils.capitalize;

public class TestGenerator {

    public static final String DATA_FILE_SUFFIX = ".yaml";

    public static void createTests(Resource resource, String packageName, SourceRoot sourceRoot, SourceRoot resourceRoot, AbacAuthModel abacAuthModel, String modelFileName) throws IOException, EasyAbacInitException {
        if (resource.getActions() == null || resource.getActions().isEmpty()) {
            return;
        }

        // generating Deny tests
        createDenyTestClass(resource, packageName, sourceRoot, resourceRoot, abacAuthModel, modelFileName);

        // generating Permit tests
        createPermitTestClass(resource, packageName, sourceRoot, resourceRoot, abacAuthModel, modelFileName);
    }

    private static void createDenyTestClass(Resource resource, String packageName, SourceRoot sourceRoot, SourceRoot resourceRoot, AbacAuthModel abacAuthModel, String modelFileName) throws IOException, EasyAbacInitException {
        CompilationUnit testUnit = new CompilationUnit(packageName);
        String resourceName = capitalize(resource.getId());
        String testName = "EasyABAC_" + resourceName + "_Deny_Test";
        ClassOrInterfaceDeclaration type = createType(testUnit, testName, resource, packageName);

        createConstructor(type, testName, modelFileName);
        createTest(type, resourceName, DENY);
        boolean atLeastOne = createData(type, abacAuthModel, testName, resource, resourceRoot, resourceName, DENY, packageName);

        if (atLeastOne) {
            testUnit.setStorage(resolvePathForSourceFile(sourceRoot, packageName, testName));
            sourceRoot.add(testUnit);
        }
    }

    private static void createPermitTestClass(Resource resource, String packageName, SourceRoot sourceRoot, SourceRoot resourceRoot, AbacAuthModel abacAuthModel, String modelFileName) throws IOException, EasyAbacInitException {
        CompilationUnit testUnit = new CompilationUnit(packageName);
        String resourceName = capitalize(resource.getId());
        String testName = "EasyABAC_" + resourceName + "_Permit_Test";
        ClassOrInterfaceDeclaration type = createType(testUnit, testName, resource, packageName);

        createConstructor(type, testName, modelFileName);
        createTest(type, resourceName, PERMIT);
        boolean atLeastOne = createData(type, abacAuthModel, testName, resource, resourceRoot, resourceName, PERMIT, packageName);

        if (atLeastOne) {
            testUnit.setStorage(resolvePathForSourceFile(sourceRoot, packageName, testName));
            sourceRoot.add(testUnit);
        }
    }

    private static void createConstructor(ClassOrInterfaceDeclaration type, String testName, String modelFileName) {
        ConstructorDeclaration constructor = type.addConstructor(Modifier.PUBLIC);
        constructor.addThrownException(Exception.class);
        BlockStmt body = constructor.getBody();
        body.addStatement("super(loadModel(" + testName + ".class, \"" + modelFileName + "\"));");
    }

    private static boolean createData(ClassOrInterfaceDeclaration type, AbacAuthModel abacAuthModel, String testName, Resource resource, SourceRoot resourceRoot, String resourceName, AuthResponse.Decision decision, String packageName) throws IOException, EasyAbacInitException {
        createDataMethod(type, testName, decision, resourceName);

        TestGenerationAlgorithm algorithm = CombinationAlgorithmFactory.getByCode(abacAuthModel.getCombiningAlgorithm());
        List<Map<String, String>> tests = algorithm.generatePolicies(abacAuthModel.getPolicies(), decision == PERMIT ? Effect.PERMIT : Effect.DENY);

        Yaml yaml = new Yaml();
        for (int i = 0; i < tests.size(); i++) {
            TestDescription testDescription = new TestDescription();
            Map<String, String> data = tests.get(i);
            String action = data.remove(FunctionUtils.ACTION);
            testDescription.setAction(action);

            Map<String, Object> prettyData = beautifyValues(abacAuthModel, data);

            Map<String, Map<String, Object>> structMap = new HashMap<>();
            prettyData.entrySet().stream()
                    .forEach(stringStringEntry -> {
                        String key = stringStringEntry.getKey();
                        String entity = key.substring(0, key.indexOf("."));
                        Map<String, Object> attrMap = structMap.computeIfAbsent(entity, s -> new HashMap<>());
                        attrMap.put(key.substring(entity.length() + 1), stringStringEntry.getValue());
                    });


            testDescription.setAttributes(structMap);

            // creating folders
            String folderName = resourceRoot.getRoot().toString() + "/" + packageName.replace(".", "/");
            new File(folderName).mkdirs();
            String dataFileName = folderName + "/" + resource.getId().toLowerCase() + "_" + decision.name().toLowerCase() + "_" + i + DATA_FILE_SUFFIX;
            File dataFile = new File(dataFileName);
            if (!dataFile.exists()) {
                dataFile.createNewFile();
            }
            FileWriter writer = new FileWriter(dataFile);
            yaml.dump(testDescription, writer);
        }
        return tests.size() > 0;
    }

    /**
     * Making values pretty and make concrete type
     */
    private static Map<String, Object> beautifyValues(AbacAuthModel abacAuthModel, Map<String, String> data) {
        Map<String, Object> beautifullMap = new HashMap<>();
        Map<String, Object> mapping = new HashMap<>();
        Map<String, Integer> attributeCounter = new HashMap<>();
        Map<String, Attribute> attributes = abacAuthModel.getAttributes();


        // process terminal elements
        data.forEach((attributeKey, value) -> {
            Attribute attribute = attributes.get(attributeKey);
            Object newValue = value;

            if (value.startsWith(FunctionUtils.UNKNOWN_PREFIX)) {
                // should be beautified
                if (mapping.containsKey(value)) {
                    newValue = mapping.get(value);
                } else {
                    Integer counter = attributeCounter.computeIfAbsent(attributeKey, s -> 0);
                    ++counter;

                    if (attribute.getType() == DataType.STRING) {
                        newValue = attributeKey + "_" + counter;
                    } else if (attribute.getType() == DataType.INT) {
                        newValue = 1000; // to be in positive range
                    }
                    mapping.put(value, newValue);
                }
            }
            beautifullMap.put(attributeKey, newValue);
        });

        // process function elements
        beautifullMap.forEach((attributeKey, value) -> {
            Attribute attribute = attributes.get(attributeKey);
            if (value instanceof String) {
                // possible function
                String strValue = value.toString();
                if (strValue.startsWith(ANY_FUNCTION)) {
                    Object newValue = processFunctional(strValue, beautifullMap, mapping, attributeKey);
                    mapping.put(strValue, newValue);
                    beautifullMap.put(attributeKey, newValue);
                }
            }


        });

        return beautifullMap;
    }

    private static Object processFunctional(String strValue, Map<String, Object> beautifullMap, Map<String, Object> mapping, String attributeKey) {
        if (strValue.startsWith(ANY_FUNCTION)) {
            String withoutPrefix = strValue.substring(ANY_FUNCTION.length() + FunctionUtils.FUNCTION_CODE_LENGTH);
            String function = strValue.substring(0, ANY_FUNCTION.length() + FunctionUtils.FUNCTION_CODE_LENGTH);
            Object nestedValue = processFunctional(withoutPrefix, beautifullMap, mapping, attributeKey);
            // apply function
            return FunctionUtils.calculateValue(nestedValue, function);

        } else {
            return mapping.getOrDefault(strValue, strValue);
        }
    }

    private static void createDataMethod(ClassOrInterfaceDeclaration type, String testName, AuthResponse.Decision decision, String resourceName) {
        MethodDeclaration method = type.addMethod("data", Modifier.PUBLIC, Modifier.STATIC);
        method.addThrownException(Exception.class);
        method.setType("List<Object[]>");
        NormalAnnotationExpr annotation = method.addAndGetAnnotation(Parameterized.Parameters.class);
        annotation.addPair("name", new StringLiteralExpr("{index}: resource({0}) and action({1})"));

        BlockStmt body = new BlockStmt();
        body.addStatement("return generateTestData(" + testName + ".class,\n " + resourceName + ".class,\n "
                + resourceName + ACTION_SUFFIX + ".class, " + decision.name() + ");");
        method.setBody(body);
    }

    private static void createTest(ClassOrInterfaceDeclaration type, String resourceName, AuthResponse.Decision decision) {
        MethodDeclaration method = type.addMethod("test_" + decision.name(), Modifier.PUBLIC);
        method.addThrownException(Exception.class);
        if (decision == DENY) {
            NormalAnnotationExpr annotation = method.addAndGetAnnotation(Test.class);
            annotation.addPair("expected", new ClassExpr(new ClassOrInterfaceType(NotPermittedException.class.getSimpleName())));
        } else {
            method.addMarkerAnnotation(Test.class);
        }

        BlockStmt body = new BlockStmt();
        body.addStatement("getPermissionChecker(" + resourceName + ".class).ensurePermitted(resource, action);");

        method.setBody(body);
    }

    private static ClassOrInterfaceDeclaration createType(CompilationUnit testUnit, String name, Resource resource, String packageName) {
        for (ImportDeclaration annotationImport : IMPORTS) {
            testUnit.addImport(annotationImport);
        }
        testUnit.addImport(new ImportDeclaration(packageName + CompleteGenerator.MODEL_SUFFIX, false, true));

        String javaName = capitalize(name);

        Comment typeComment = new JavadocComment("Testing entity \"" + resource.getTitle() + "\"");
        testUnit.addOrphanComment(typeComment);

        ClassOrInterfaceDeclaration type = testUnit.addClass(javaName);
        ClassOrInterfaceDeclaration ext = type.addExtendedType(EasyAbacBaseTestClass.class.getSimpleName());

        return type;
    }

    private static void createTestsForCase(ClassOrInterfaceDeclaration type, Resource resource, String action, List<Policy> permissions) {
        String genAction = resource.getId() + "." + action;
        createPermitTestOld(type, action, resource);
        for (Policy permission : permissions) {
            if (permission.getTarget().getAccessToActions().contains(genAction)) {
                permission.getRules().forEach(rule -> createDenyTestOld(type, action, resource.getId(), rule));
            }
        }


    }

    private static void createDenyTestOld(ClassOrInterfaceDeclaration type, String value, String entityName, Rule rule) {
        String enumValue = value.toUpperCase();

        MethodDeclaration method = type.addMethod("test_" + enumValue + "_Deny_" + rule.getId(), Modifier.PUBLIC);
        NormalAnnotationExpr annotation = method.addAndGetAnnotation(Test.class);
        annotation.addPair("expected", new ClassExpr(new ClassOrInterfaceType(NotPermittedException.class.getSimpleName())));

        BlockStmt body = new BlockStmt();
        body.addStatement("permissionChecker.ensurePermitted(data_" + enumValue + "_Deny_" + rule.getId() + "(), " + enumValue + ");");

        method.setBody(body);


        MethodDeclaration dataMethod = type.addMethod("data_" + enumValue + "_Deny_" + rule.getId(), Modifier.PRIVATE);
        dataMethod.setType(capitalize(entityName));

        BlockStmt data = new BlockStmt();
        data.addStatement("return null;");
        dataMethod.setBody(data);
    }

    private static void createPermitTestOld(ClassOrInterfaceDeclaration type, String value, Resource resource) {
        String enumValue = value.toUpperCase();
        MethodDeclaration method = type.addMethod("test_" + enumValue + "_Permit", Modifier.PUBLIC);
        method.addMarkerAnnotation(Ignore.class);
        method.addMarkerAnnotation(Test.class);


        BlockStmt body = new BlockStmt();
        body.addStatement("permissionChecker.ensurePermitted(data_" + enumValue + "_Permit(), " + enumValue + ");");

        method.setBody(body);

        MethodDeclaration dataMethod = type.addMethod("data_" + enumValue + "_Permit", Modifier.PRIVATE);
        dataMethod.setType(capitalize(resource.getId()));

        BlockStmt data = new BlockStmt();
        data.addStatement("return null;");
        dataMethod.setBody(data);
    }

    private static List<ImportDeclaration> IMPORTS = new ArrayList<ImportDeclaration>() {
        {
            add(new ImportDeclaration(NotPermittedException.class.getName(), false, false));
            add(new ImportDeclaration(EasyAbacBaseTestClass.class.getName(), false, false));
            add(new ImportDeclaration("custis.easyabac.pdp.AuthResponse.Decision", true, true));

            // test
            add(new ImportDeclaration(Ignore.class.getName(), false, false));
            add(new ImportDeclaration(Test.class.getName(), false, false));
            add(new ImportDeclaration(List.class.getName(), false, false));

            // general
        }
    };

}