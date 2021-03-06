package custis.easyabac.core.pdp.balana;

import custis.easyabac.core.EasyAbac;
import custis.easyabac.core.pdp.*;
import custis.easyabac.core.pdp.balana.trace.BalanaTraceHandler;
import custis.easyabac.core.pdp.balana.trace.BalanaTraceHandlerProvider;
import custis.easyabac.core.trace.model.TraceResult;
import custis.easyabac.model.AbacAuthModel;
import custis.easyabac.model.EasyAbacInitException;
import custis.easyabac.model.IdGenerator;
import custis.easyabac.model.attribute.AttributeWithValue;
import custis.easyabac.model.attribute.Category;
import custis.easyabac.model.attribute.DataType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.balana.PDP;
import org.wso2.balana.attr.StringAttribute;
import org.wso2.balana.ctx.AbstractResult;
import org.wso2.balana.ctx.Attribute;
import org.wso2.balana.ctx.AttributeAssignment;
import org.wso2.balana.ctx.ResponseCtx;
import org.wso2.balana.ctx.xacml3.RequestCtx;
import org.wso2.balana.ctx.xacml3.Result;
import org.wso2.balana.xacml3.*;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static custis.easyabac.core.pdp.balana.BalanaAttributesFactory.ATTRIBUTE_REQUEST_ID;
import static custis.easyabac.core.pdp.balana.BalanaAttributesFactory.balanaAttribute;
import static custis.easyabac.core.pdp.balana.XacmlConstants.findXacmlName;
import static java.util.stream.Collectors.toSet;

public class BalanaPdpHandler implements PdpHandler {

    private final static Log log = LogFactory.getLog(EasyAbac.class);

    private final AbacAuthModel abacAuthModel;
    private final PDP pdp;
    private final boolean xacmlPolicyMode;

    public BalanaPdpHandler(AbacAuthModel abacAuthModel, PDP pdp, boolean xacmlPolicyMode) {
        this.abacAuthModel = abacAuthModel;
        this.pdp = pdp;
        this.xacmlPolicyMode = xacmlPolicyMode;
    }

    @Override
    public AuthResponse evaluate(List<AttributeWithValue> attributeWithValues) {

        RequestId requestId = RequestId.newRandom();
        addRequestIdAttribute(requestId, attributeWithValues);

        Map<Category, Attributes> attributesMap;

        try {
            attributesMap = getBalanaAttributesByCategory(attributeWithValues);
        } catch (EasyAbacInitException e) {
            return new AuthResponse(e.getMessage());
        }
        RequestCtx requestCtx = new RequestCtx(new HashSet<>(attributesMap.values()), null);

        BalanaTraceHandler balanaTraceHandler = BalanaTraceHandlerProvider.instantiate();
        ResponseCtx responseCtx = pdp.evaluate(requestCtx);

        Map<RequestId, TraceResult> results = BalanaTraceHandlerProvider.get().getResults();
        return createResponse(responseCtx.getResults().iterator().next(), results.get(requestId));
    }

    @Override
    public MultiAuthResponse evaluate(MultiAuthRequest multiAuthRequest) throws EasyAbacInitException {
        return evaluate(multiAuthRequest.getRequests());
    }

    private MultiAuthResponse evaluate(Map<RequestId, List<AttributeWithValue>> requests) throws EasyAbacInitException {
        Set<RequestReference> requestReferences = new HashSet<>();
        Set<Attributes> attributesSet = new HashSet<>();
        for (RequestId requestId : requests.keySet()) {

            List<AttributeWithValue> attributeWithValues = requests.get(requestId);

            addRequestIdAttribute(requestId, attributeWithValues);

            Map<Category, Attributes> balanaAttributesByCategory = getBalanaAttributesByCategory(attributeWithValues);

            RequestReference requestReference = transformReference(balanaAttributesByCategory);
            requestReferences.add(requestReference);

            attributesSet.addAll(balanaAttributesByCategory.values());
        }
        MultiRequests multiRequests = new MultiRequests(requestReferences);


        RequestCtx requestCtx = new RequestCtx(null, attributesSet, false, false, multiRequests, null);
        BalanaTraceHandler balanaTraceHandler = BalanaTraceHandlerProvider.instantiate();
        if (log.isDebugEnabled()) {
            requestCtx.encode(System.out);
        }

        ResponseCtx responseCtx = pdp.evaluate(requestCtx);

        if (log.isDebugEnabled()) {
            log.debug(responseCtx.encode());
        }

        Map<RequestId, AuthResponse> results = new HashMap<>();

        for (AbstractResult abstractResult : responseCtx.getResults()) {
            Result result = (Result) abstractResult;

            Stream<Attributes> envAttributes = result.getAttributes()
                    .stream()
                    .filter(attributes -> attributes.getCategory().toString().equals(findXacmlName(Category.ENV)));

            Optional<Attribute> requestId = envAttributes.flatMap(attributes -> attributes.getAttributes().stream())
                    .filter(attribute -> attribute.getId().toString().equals("request-id")).findFirst();

            if (!requestId.isPresent()) {
                throw new RuntimeException("Not found requestId in response");
            }

            StringAttribute value = (StringAttribute) requestId.get().getValue();

            Map<RequestId, TraceResult> traceResults = BalanaTraceHandlerProvider.get().getResults();
            results.put(RequestId.of(value.getValue()), createResponse(abstractResult, traceResults.get(RequestId.of(value.getValue()))));

        }

        return new MultiAuthResponse(results);
    }

    @Override
    public MultiAuthResponse evaluate(MultiAuthRequestOptimize multiAuthRequest) throws EasyAbacInitException {
        Map<RequestId, List<AttributeWithValue>> requests = new HashMap<>();

        Map<String, custis.easyabac.model.attribute.Attribute> modelAttributes = abacAuthModel.getAttributes();

        multiAuthRequest.getRequests().forEach((requestId, attributeWithValueIds) -> {
            List<AttributeWithValue> attributeWithValues = attributeWithValueIds.stream().map(o -> {
                AuthAttribute authAttribute = multiAuthRequest.getAttributesWithValue().get(o);
                return new AttributeWithValue(modelAttributes.get(authAttribute.getId()), authAttribute.getValues());
            }).collect(Collectors.toList());
            requests.put(requestId, attributeWithValues);
        });

        return evaluate(requests);
    }


    private void addRequestIdAttribute(RequestId requestId, List<AttributeWithValue> attributeWithValues) {
        AttributeWithValue requestIdAttribute = new AttributeWithValue(new custis.easyabac.model.attribute.Attribute(ATTRIBUTE_REQUEST_ID, Category.ENV, DataType.STRING),
                Collections.singletonList(requestId.getId()));

        attributeWithValues.add(requestIdAttribute);
    }

    private RequestReference transformReference(Map<Category, Attributes> balanaAttributesByCategory) {
        Set<AttributesReference> references = new HashSet<>();
        for (Attributes attributes : balanaAttributesByCategory.values()) {
            AttributesReference reference = new AttributesReference();
            reference.setId(attributes.getId());
            references.add(reference);
        }
        RequestReference requestReference = new RequestReference();
        requestReference.setReferences(references);
        return requestReference;
    }

    private Map<Category, Attributes> getBalanaAttributesByCategory(List<AttributeWithValue> attributeWithValues) throws EasyAbacInitException {
        Map<Category, Attributes> attributesMap = new HashMap<>();

        for (AttributeWithValue attributeWithValue : attributeWithValues) {
            Category cat = attributeWithValue.getAttribute().getCategory();

            Attributes attributes = attributesMap.computeIfAbsent(cat,
                    category -> new Attributes(URI.create(findXacmlName(category)), null, new HashSet<>(), IdGenerator.newId())
            );
            boolean includeInResult = attributeWithValue.getAttribute().getId().equals(ATTRIBUTE_REQUEST_ID);


            Attribute newBalanaAttribute = transformAttributeValue(attributeWithValue, includeInResult);

            attributes.getAttributes().add(newBalanaAttribute);
        }

        return attributesMap;
    }


    private Attribute transformAttributeValue(AttributeWithValue attributeWithValue, boolean includeInResult) throws EasyAbacInitException {

        custis.easyabac.model.attribute.Attribute attribute = attributeWithValue.getAttribute();
        return balanaAttribute(attribute.getXacmlName(), attribute.getType(), attributeWithValue.getValues(), includeInResult);
    }

    @Override
    public boolean xacmlPolicyMode() {
        return xacmlPolicyMode;
    }

    private AuthResponse createResponse(AbstractResult abstractResult, TraceResult traceResult) {
        AuthResponse.Decision decision = AuthResponse.Decision.getByIndex(abstractResult.getDecision());
        Set<AttributeAssignment> assignments = abstractResult.getObligations()
                .stream()
                .filter(obligationResult -> obligationResult instanceof Obligation)
                .flatMap(obligationResult -> ((Obligation) obligationResult).getAssignments().stream())
                .collect(toSet());
        Map<String, String> obligations = new HashMap<>();
        for (AttributeAssignment assignment : assignments) {
            obligations.put(assignment.getAttributeId().toString(), assignment.getContent());
        }


        return new AuthResponse(decision, obligations, traceResult);
    }

}
