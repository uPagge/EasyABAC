package custis.easyabac;

import custis.easyabac.core.EasyAbac;
import custis.easyabac.core.init.Datasource;
import custis.easyabac.core.init.Param;
import custis.easyabac.pdp.AttributiveAuthorizationService;
import custis.easyabac.pdp.AuthAttribute;
import custis.easyabac.pdp.AuthResponse;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.*;

public class PipTest {

    private static final String ACTION_OPERATION = "report.action";
    private static final String RESOURCE_CATEGORY = "report.category";
    private static final String SUBJECT_SUBJECT_ID = "subject.id";
    private static final String SUBJECT_ALLOWED_CATEGORIES = "subject.allowed-categories";

    private InputStream getResourceAsStream(String s) {
        return this.getClass()
                .getClassLoader()
                .getResourceAsStream(s);
    }

    @Test
    public void TwoAttrEquelsTest() throws Exception {
        InputStream policy = getResourceAsStream("test_pip_policy.xacml");
        InputStream easyModel = getResourceAsStream("test_init_xacml.yaml");
        AttributiveAuthorizationService authorizationService = new EasyAbac.Builder(easyModel, ModelType.XACML).xacmlPolicy(policy).build();

        List<AuthAttribute> authAttrList = new ArrayList<>();
        authAttrList.add(new AuthAttribute(ACTION_OPERATION, "edit"));
        authAttrList.add(new AuthAttribute(RESOURCE_CATEGORY, "iod"));
        authAttrList.add(new AuthAttribute(SUBJECT_ALLOWED_CATEGORIES, Arrays.asList("iod", "dsp")));
        AuthResponse authResponse = authorizationService.authorize(authAttrList);
        Assert.assertEquals(AuthResponse.Decision.PERMIT, authResponse.getDecision());
    }

    @Test
    public void SamplePipTest() throws Exception {
        InputStream policy = getResourceAsStream("test_pip_policy.xacml");
        InputStream easyModel = getResourceAsStream("test_init_xacml.yaml");

        HashSet<Param> params = new HashSet<>();
        Param userName = new Param("userName", SUBJECT_SUBJECT_ID);
        params.add(userName);

        Datasource datasource = new SampleDatasource(params, SUBJECT_ALLOWED_CATEGORIES);

        AttributiveAuthorizationService authorizationService = new EasyAbac.Builder(easyModel, ModelType.XACML).xacmlPolicy(policy).datasources(Collections.singletonList(datasource)).build();

        List<AuthAttribute> authAttrList = new ArrayList<>();
        authAttrList.add(new AuthAttribute(ACTION_OPERATION, "edit"));
        authAttrList.add(new AuthAttribute(RESOURCE_CATEGORY, "iod"));
        authAttrList.add(new AuthAttribute(SUBJECT_SUBJECT_ID, "bob"));
        AuthResponse authResponse = authorizationService.authorize(authAttrList);
        Assert.assertEquals(AuthResponse.Decision.PERMIT, authResponse.getDecision());


        authAttrList.add(new AuthAttribute(ACTION_OPERATION, "edit"));
        authAttrList.add(new AuthAttribute(RESOURCE_CATEGORY, "iod"));
        authAttrList.add(new AuthAttribute(SUBJECT_SUBJECT_ID, "alice"));
        authResponse = authorizationService.authorize(authAttrList);
        Assert.assertEquals(AuthResponse.Decision.DENY, authResponse.getDecision());

    }


    class SampleDatasource extends Datasource {

        public SampleDatasource(Set<Param> params, String requiredAttributeId) {
            super(params, requiredAttributeId);
        }

        public SampleDatasource(Set<Param> params, String requiredAttributeId, Long expire) {
            super(params, requiredAttributeId, expire);
        }

        @Override
        public List<String> find() {
            {
                String userName = null;
                for (Param param : getParams()) {
                    if (param.getName().equals("userName")) {
                        userName = param.getValue();
                    }
                }

                if (userName != null) {
                    switch (userName) {
                        case "bob":
                            return Arrays.asList("iod", "dsp");
                        case "alice":
                            return Arrays.asList("dsp");
                        case "peter":
                            return Arrays.asList("iod");
                    }
                }
                return null;
            }
        }
    }


}
