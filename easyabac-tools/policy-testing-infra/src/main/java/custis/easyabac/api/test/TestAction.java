package custis.easyabac.api.test;

public class TestAction {

    private String id;
    private String value;

    public TestAction() {
    }

    public TestAction(String id, String value) {
        this.id = id;
        this.value = value;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
