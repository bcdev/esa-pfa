package org.esa.rss.pfa.fe.op;

/**
* @author Norman Fomferra
*/
public class FeatureType extends AttributeType {
    private final AttributeType[] attributeTypes;

    public FeatureType(String name, String description, Class<?> valueType, AttributeType... attributeTypes) {
        super(name, description, valueType);
        this.attributeTypes = attributeTypes;
    }

    public AttributeType[] getAttributeTypes() {
        return attributeTypes;
    }
}
