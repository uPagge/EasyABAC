package custis.easyabac.api.core.call.callprocessor;

import custis.easyabac.api.core.PermissionCheckerInformation;
import custis.easyabac.api.core.call.DecisionType;
import custis.easyabac.api.core.call.MethodType;
import custis.easyabac.api.core.call.converters.ResultConverter;
import custis.easyabac.api.core.call.getters.AttributesValuesGetterFactory;
import custis.easyabac.api.core.call.getters.RequestGenerator;
import custis.easyabac.api.core.call.getters.RequestWrapper;
import custis.easyabac.core.pdp.AuthResponse;
import custis.easyabac.core.pdp.AuthService;
import custis.easyabac.core.pdp.RequestId;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class MethodCallProcessor {
    protected final AuthService authService;
    protected final Method method;
    protected final PermissionCheckerInformation checkerInfo;
    protected final MethodType methodType;
    protected final DecisionType decisionType;
    protected RequestGenerator valuesGetter;
    protected ResultConverter resultConverter;

    protected MethodCallProcessor(PermissionCheckerInformation checkerInfo, Method method, AuthService authService) {
        this.checkerInfo = checkerInfo;
        this.method = method;
        this.authService = authService;
        this.methodType = MethodType.findByMethod(method);
        this.decisionType = DecisionType.findByMethod(method, methodType);
    }

    public Object execute(List<Object> arguments) {
        // preparing requests
        RequestWrapper reqWrapper = valuesGetter.generate(arguments);

        // executing requests
        Map<RequestId, AuthResponse> responses = authService.authorizeMultiple(reqWrapper.getRequests());

        // processing result
        return resultConverter.convert(reqWrapper.getMapping(), responses);
    }

    protected abstract Optional<RequestGenerator> prepareCustomAttributesValuesGetter();

    protected abstract ResultConverter prepareResultConverter();

    public void afterPropertiesSet() {
        this.valuesGetter = prepareAttributesValuesGetter();
        this.resultConverter = prepareResultConverter();
    }

    private RequestGenerator prepareAttributesValuesGetter() {
        Optional<RequestGenerator> optional = prepareCustomAttributesValuesGetter();
        if (optional.isPresent()) {
            return optional.get();
        }
        try {
            return AttributesValuesGetterFactory.prepareDefault(method, checkerInfo, methodType, decisionType);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


}

