package org.esa.pfa.ordering;

import org.openide.util.NbPreferences;

import java.io.File;
import java.util.prefs.Preferences;

/**
 * @author Norman Fomferra
 */
public class ProductAccessOptions {

    private final static ProductAccessOptions INSTANCE = new ProductAccessOptions();
    private final static Preferences preferences = NbPreferences.forModule(ProductAccessOptions.class);

    public static ProductAccessOptions getDefault() {
        return INSTANCE;
    }

    public static Preferences getPreferences() {
        return preferences;
    }

    public String getDefaultUrl() {
        return preferences.get("productAccess.defaultUrl", "");
    }

    public void setDefaultUrl(String url) {
        preferences.put("productAccess.defaultUrl", url);
    }

    public boolean getCustomCliEnabled() {
        return preferences.getBoolean("productAccess.customCli.enabled", false);
    }

    public void setCustomCliEnabled(boolean enabled) {
        preferences.putBoolean("productAccess.customCli.enabled", enabled);
    }

    public String getCustomCli() {
        return preferences.get("productAccess.customCli", "");
    }

    public void setCustomCli(String url) {
        preferences.put("productAccess.customCli", url);
    }

    public boolean getLocalPathsEnabled() {
        return preferences.getBoolean("productAccess.localPaths.enabled", true);
    }

    public void setLocalPathsEnabled(boolean enabled) {
        preferences.putBoolean("productAccess.localPaths.enabled", enabled);
    }

    public String[] getLocalPaths() {
        return preferences.get("productAccess.localPaths", "").split(File.pathSeparator);
    }

    public void setLocalPaths(String[] url) {
        preferences.put("productAccess.localPaths", String.join(File.pathSeparator));
    }

}
