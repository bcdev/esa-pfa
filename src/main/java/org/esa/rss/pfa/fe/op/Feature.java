package org.esa.rss.pfa.fe.op;

/**
* @author Norman Fomferra
*/
public class Feature<T> {
    private final FeatureType featureType;
    private final T value;
    private final Object[] attributeValues;

    public Feature(FeatureType featureType, T value, Object... attributeValues) {
        this.featureType = featureType;
        this.value = value;
        this.attributeValues = attributeValues;
    }

    public FeatureType getFeatureType() {
        return featureType;
    }

    public T getValue() {
        return value;
    }

    public Object[] getAttributeValues() {
        return attributeValues;
    }
}
