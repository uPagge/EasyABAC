package custis.easyabac.core.model.attribute.load;

import java.util.Set;

public class EasyAttribute {
    private String code;
    private String title;
    private String type = "string";
    private boolean multiple = false;
    private Set<String> allowableValues;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isMultiple() {
        return multiple;
    }

    public void setMultiple(boolean multiple) {
        this.multiple = multiple;
    }

    public Set<String> getAllowableValues() {
        return allowableValues;
    }

    public void setAllowableValues(Set<String> allowableValues) {
        this.allowableValues = allowableValues;
    }
}
