package custis.easyabac.generation;

import com.github.javaparser.utils.SourceRoot;
import custis.easyabac.model.AbacAuthModel;
import custis.easyabac.model.EasyAbacInitException;
import custis.easyabac.model.attribute.Resource;
import custis.easyabac.model.easy.EasyAbacModelCreator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class CompleteGenerator {
    static final String MODEL_SUFFIX = ".model";

    public static void generate(InputStream is, Path testSourcePath, Path testResourcePath, String testBasePackage, String modelFileName) throws EasyAbacInitException, IOException {
        EasyAbacModelCreator creator = new EasyAbacModelCreator();
        AbacAuthModel model = creator.createModel(is);
        SourceRoot sourceRoot = new SourceRoot(testSourcePath);
        SourceRoot resourceRoot = new SourceRoot(testResourcePath);

        for (Resource resource : model.getResources().values()) {
            if (!resource.getActions().isEmpty()) {
                TestGenerator.createTests(resource, testBasePackage, sourceRoot, resourceRoot, model, modelFileName);
            }
        }



        sourceRoot.saveAll();
    }
}
