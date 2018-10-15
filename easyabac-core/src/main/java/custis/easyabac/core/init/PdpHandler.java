package custis.easyabac.core.init;

import custis.easyabac.core.model.abac.attribute.AttributeWithValue;
import custis.easyabac.pdp.AuthResponse;
import custis.easyabac.pdp.MultiAuthRequest;
import custis.easyabac.pdp.MultiAuthResponse;

import java.util.List;

public interface PdpHandler {
    AuthResponse evaluate(List<AttributeWithValue> attributeWithValues);

    MdpAuthResponse evaluate(MdpAuthRequest mdpAuthRequest);

    boolean xacmlPolicyMode();
    MultiAuthResponse evaluate(MultiAuthRequest multiAuthRequest) throws EasyAbacInitException;
}
