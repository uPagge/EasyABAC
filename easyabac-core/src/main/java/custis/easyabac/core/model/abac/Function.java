package custis.easyabac.core.model.abac;

public enum Function {
    EQUAL("=="),
    GREATER(">"),
    LESS("<"),
    GREATER_OR_EQUEL(">="),
    LESS_OR_EQUEL("<="),
    IN("in"),
    ONE_OF("one-of"),
    SUBSET("subset");
    private String functionName;

    Function(String functionName) {
        this.functionName = functionName;
    }

}