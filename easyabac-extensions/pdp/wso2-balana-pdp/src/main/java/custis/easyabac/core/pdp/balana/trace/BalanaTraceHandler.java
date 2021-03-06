package custis.easyabac.core.pdp.balana.trace;

import custis.easyabac.core.EasyAbacAuthException;
import custis.easyabac.core.pdp.RequestId;
import custis.easyabac.core.trace.model.*;
import custis.easyabac.model.EasyAbacInitException;
import custis.easyabac.model.attribute.Attribute;
import custis.easyabac.model.attribute.Category;
import custis.easyabac.model.attribute.DataType;
import org.wso2.balana.*;
import org.wso2.balana.attr.AttributeValue;
import org.wso2.balana.attr.BagAttribute;
import org.wso2.balana.combine.PolicyCombiningAlgorithm;
import org.wso2.balana.combine.RuleCombiningAlgorithm;
import org.wso2.balana.cond.Condition;
import org.wso2.balana.cond.EvaluationResult;
import org.wso2.balana.cond.Expression;
import org.wso2.balana.ctx.AbstractResult;
import org.wso2.balana.ctx.EvaluationCtx;
import org.wso2.balana.ctx.ResponseCtx;
import org.wso2.balana.ctx.Status;
import org.wso2.balana.ctx.xacml3.RequestCtx;
import org.wso2.balana.ctx.xacml3.XACML3EvaluationCtx;
import org.wso2.balana.finder.PolicyFinderResult;
import org.wso2.balana.xacml3.Attributes;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static custis.easyabac.core.pdp.balana.BalanaAttributesFactory.ATTRIBUTE_REQUEST_ID;
import static custis.easyabac.core.pdp.balana.XacmlConstants.*;

/**
 * Holder of Trace process
 */
public class BalanaTraceHandler {

    private final Stack<Object> callStack = new Stack<Object>();
    private final Map<Attribute, BagAttribute> attributeMap = new HashMap<Attribute, BagAttribute>();
    private final Map<Attribute, Status> attributesStatusMap = new HashMap<Attribute, Status>();

    private Map<RequestId, TraceResult> traceResults = new LinkedHashMap<>();

    public void onFindPolicyStart(EvaluationCtx evaluationCtx) {
        // NEW part of request

        if (!callStack.empty()) {
            finalizeTraceResult();
        }

        String requestIdValue = extractRequestId(evaluationCtx);

        RequestId requestId = RequestId.of(requestIdValue);

        TraceResult traceResult = new TraceResult(requestId);
        traceResults.put(requestId, traceResult);
        callStack.push(evaluationCtx);
        callStack.push(traceResult);

    }

    private String extractRequestId(EvaluationCtx context) {

        EvaluationResult result = context.getAttribute(URI.create(findXacmlName(DataType.STRING)),
                URI.create(ATTRIBUTE_REQUEST_ID), "", URI.create(findXacmlName(Category.ENV)));

        String errorMessage = "Attribute request-id is not found in the context";

        if (result.indeterminate()) {
            throw new EasyAbacAuthException(errorMessage);
        }

        if (result != null && result.getAttributeValue() != null && result.getAttributeValue().isBag()) {
            BagAttribute returnBag = (BagAttribute) (result.getAttributeValue());
            if (returnBag.isEmpty()) {
                throw new EasyAbacAuthException(errorMessage);
            }
            return ((AttributeValue) returnBag.iterator().next()).encode();
        } else {
            throw new EasyAbacAuthException(errorMessage);
        }
    }


    private void finalizeTraceResult() {
        TraceResult traceResult = (TraceResult) callStack.pop();
        EvaluationCtx evalCtx = (EvaluationCtx) callStack.pop();


        for (Attributes attributes : ((XACML3EvaluationCtx) evalCtx).getAttributesSet()) {
            for (org.wso2.balana.ctx.Attribute attribute : attributes.getAttributes()) {
                List<String> convertedValues = attribute.getValues()
                        .stream()
                        .map(
                                attributeValue -> attributeValue.encode()
                        )
                        .collect(Collectors.toList());
                traceResult.putAttribute(CalculatedAttribute.of(attribute.getId().toString(), convertedValues));
            }
        }

        attributeMap.forEach((attribute, bagAttribute) -> {
            Iterator iter = bagAttribute.iterator();
            List<String> convertedValues = new ArrayList<>();
            while (iter.hasNext()) {
                AttributeValue val = (AttributeValue) iter.next();
                convertedValues.add(val.encode());
            }
            traceResult.putAttribute(CalculatedAttribute.of(attribute.getId(), convertedValues));
        });
    }

    public void onFindPolicyEnd(PolicyFinderResult policyFinderResult) {
        // nothing to do
    }


    public void onPolicyMatchStart(AbstractPolicy policy) {
        safePolicyEnd(policy);

        Object top = callStack.peek();
        Object almostTop = callStack.get(callStack.size() - 2);
        if (policy instanceof PolicySet) {
            CalculatedPolicySet ps = new CalculatedPolicySet(policy.getId());
            if (almostTop instanceof CalculatedPolicySet) {
                ((CalculatedPolicySet) almostTop).addInnerPolicySet(ps);
            } else if (top instanceof TraceResult) {
                ((TraceResult) top).setMainPolicy(ps);
            }
            callStack.push(ps);
        } else if (policy instanceof Policy) {

            CalculatedPolicy newPolicy = new CalculatedPolicy(policy.getId());
            if (almostTop instanceof CalculatedPolicySet) {
                ((CalculatedPolicySet) almostTop).addPolicy(newPolicy);
            } else if (top instanceof TraceResult) {
                ((TraceResult) top).setMainPolicy(newPolicy);
            }
            callStack.push(newPolicy);

        }


    }

    public void onPolicyMatchEnd(MatchResult realResult) {
        AbstractCalculatedPolicy top = (AbstractCalculatedPolicy) callStack.peek();

        top.setMatch(CalculatedMatch.of(realResult.getResult()));
    }


    public void onPolicyEvaluateStart(AbstractPolicy policy) {
        boolean isNewPolicy = safePolicyEnd(policy);
        if (isNewPolicy) {
            onPolicyMatchStart(policy);
            onPolicyMatchEnd(new MatchResult(MatchResult.MATCH));
        }
    }


    public void onRuleCombineStart(RuleCombiningAlgorithm combiningAlg) {
        // nothing to do
        callStack.push(combiningAlg);
    }


    public void onRuleMatchStart(Rule rule) {
        CalculatedRule calcRule = new CalculatedRule(rule.getId());
        Object policy = callStack.get(callStack.size() - 2);
        if (policy instanceof CalculatedPolicy) {
            ((CalculatedPolicy) policy).addRule(calcRule);
        } else {
            throw new ProcessingException("Rule not inside Policy!");
        }

        callStack.push(calcRule);

    }

    public void onRuleMatchEnd(MatchResult realResult) {
        CalculatedRule rule = (CalculatedRule) callStack.peek();

        rule.setMatch(CalculatedMatch.of(realResult.getResult()));
    }

    public void onRuleEvaluateStart(Rule rule) {
        // nothing to do
        if (!(callStack.peek() instanceof CalculatedRule)) {
            onRuleMatchStart(rule);
            onRuleMatchEnd(new MatchResult(MatchResult.MATCH));
        }
    }

    public void onConditionEvaluateStart(Condition condition) {

    }

    public void onConditionEvaluateEnd(EvaluationResult realResult) {

    }

    public void onRuleEvaluateEnd(AbstractResult realResult) {
        CalculatedRule rule = (CalculatedRule) callStack.pop();
        rule.setResult(CalculatedResult.of(realResult.getDecision()));
    }


    public void onRuleCombineEnd(AbstractResult result) {
        callStack.pop();
        ((CalculatedPolicy) callStack.peek()).setCombinationResult(CalculatedResult.of(result.getDecision()));
    }

    public void onPolicyEvaluateEnd(AbstractResult realResult) {
        AbstractCalculatedPolicy abstractCalculatedPolicy = (AbstractCalculatedPolicy) callStack.pop();
        abstractCalculatedPolicy.setResult(CalculatedResult.of(realResult.getDecision()));
    }


    public void onPolicyCombineStart(PolicyCombiningAlgorithm combiningAlgorithm) {
        // nothing to do
        callStack.push(combiningAlgorithm);

    }

    public void onPolicyCombineEnd(AbstractResult result) {
        safePolicyEnd(null);
        callStack.pop();
        ((AbstractCalculatedPolicy) callStack.peek()).setCombinationResult(CalculatedResult.of(result.getDecision()));
    }

    private boolean safePolicyEnd(AbstractPolicy newPolicy) {
        Object stackTop = callStack.peek();
        if (stackTop instanceof CalculatedPolicy) {
            if (newPolicy != null && newPolicy.getId().equals(((CalculatedPolicy) stackTop).getId())) {
                return false;
            }
            callStack.pop();
        } else if (stackTop instanceof CalculatedPolicySet) {
            if (newPolicy != null && newPolicy.getId().equals(((CalculatedPolicySet) stackTop).getId())) {
                return false;
            }
            callStack.pop();
        }
        return true;
    }


    public void onFindAttribute(EvaluationResult evaluationResult, String type, String attributeId, String category) throws EasyAbacInitException {
        Attribute attr = new Attribute(attributeId, findCategoryByXacmlName(category), findDataTypeByXacmlName(type));
        if (evaluationResult.indeterminate()) {
            attributesStatusMap.put(attr, evaluationResult.getStatus());
        } else {
            attributeMap.put(attr, (BagAttribute) evaluationResult.getAttributeValue());
        }
    }

    public void beforeProcess(RequestCtx evaluationCtx) {
        // nothing to do
    }

    public void postProcess(ResponseCtx realResult) {
        // nothing to do
        finalizeTraceResult();
    }

    public Map<RequestId, TraceResult> getResults() {
        return traceResults;
    }

    public void onRuleExpressionStart(Expression expression) {

    }

    public void onRuleExpressionEnd(EvaluationResult realResult) {

    }

    public void onSimpleConditionStart(int index) {
        CalculatedSimpleCondition simpleRule = new CalculatedSimpleCondition(index);
        ((CalculatedRule) callStack.peek()).addSimpleCondition(simpleRule);
        callStack.push(simpleRule);
    }

    public void onSimpleCondition(EvaluationResult realResult) {
        CalculatedSimpleCondition calculatedSimpleCondition = (CalculatedSimpleCondition) callStack.pop();
        if (realResult.indeterminate()) {
            calculatedSimpleCondition.setResult(new CalculatedResult("ERROR"));
        } else {
            calculatedSimpleCondition.setResult(new CalculatedResult(realResult.getAttributeValue().encode()));
        }
    }
}
