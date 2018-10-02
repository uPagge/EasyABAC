package custis.easyabac.core.extend.env;

import custis.easyabac.core.extend.RequestExtender;
import custis.easyabac.core.model.abac.attribute.AttributeValue;
import custis.easyabac.pdp.MdpAuthRequest;

import java.util.List;

/**
 * Extension removes not used attributes
 */
public class EnvAttributesExtender implements RequestExtender {

    @Override
    public void extend(List<AttributeValue> attributeValues) {

    }

    @Override
    public void extend(MdpAuthRequest mdpAuthRequest) {

    }
}