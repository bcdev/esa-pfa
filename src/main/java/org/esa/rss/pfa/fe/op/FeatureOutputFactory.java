package org.esa.rss.pfa.fe.op;

import org.esa.beam.framework.datamodel.Product;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Norman Fomferra
 */
public abstract class FeatureOutputFactory {

    public static final String PROPERTY_TARGET_PATH = "targetPath";
    public static final String PROPERTY_OVERWRITE_MODE = "overwriteMode";
    public static final String PROPERTY_SKIP_FEATURE_OUTPUT = "skipFeatureOutput";
    public static final String PROPERTY_SKIP_PRODUCT_OUTPUT = "skipProductOutput";
    public static final String PROPERTY_SKIP_QUICKLOOK_OUTPUT = "skipQuicklookOutput";

    private Map<String, String> properties;

    public void configure(Map<String, String> properties) {
        this.properties = new HashMap<String, String>(properties);
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public String getTargetPath() {
        return getProperty(PROPERTY_TARGET_PATH, null);
    }

    public boolean isOverwriteMode() {
        return getProperty(PROPERTY_OVERWRITE_MODE, false);
    }

    public boolean getSkipFeatureOutput() {
        return getProperty(PROPERTY_SKIP_FEATURE_OUTPUT, false);
    }

    public boolean getSkipProductOutput() {
        return getProperty(PROPERTY_SKIP_PRODUCT_OUTPUT, false);
    }

    public boolean getSkipQuicklookOutput() {
        return getProperty(PROPERTY_SKIP_QUICKLOOK_OUTPUT, false);
    }

    protected String getProperty(String key, String defaultValue) {
        String s = properties.get(key);
        return s != null ? s : defaultValue;
    }

    protected boolean getProperty(String key, boolean defaultValue) {
        String s = properties.get(key);
        return s != null ? Boolean.parseBoolean(s) : defaultValue;
    }

    public abstract FeatureOutput createFeatureOutput(Product sourceProduct) throws IOException;
}
