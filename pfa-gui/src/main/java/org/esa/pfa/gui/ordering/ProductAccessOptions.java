package org.esa.pfa.gui.ordering;

import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.gui.search.CBIRSession;
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
        return preferences.get("productAccess.defaultUrl", getDefaultDefaultUrl());
    }

    public void setDefaultUrl(String url) {
        if (url.equals(getDefaultDefaultUrl())) {
            // Allow changing default by app descriptor
            preferences.remove("productAccess.defaultUrl");
        } else {
            preferences.put("productAccess.defaultUrl", url);
        }
    }

    public boolean getCustomCommandLineEnabled() {
        return preferences.getBoolean("productAccess.customCommandLine.enabled", false);
    }

    public void setCustomCommandLineEnabled(boolean enabled) {
        preferences.putBoolean("productAccess.customCommandLine.enabled", enabled);
    }

    public String getCustomCommandLineCode() {
        return preferences.get("productAccess.customCommandLine.code", "");
    }

    public void setCustomCommandLineCode(String commandLine) {
        preferences.put("productAccess.customCommandLine.code", commandLine);
    }

    public String getCustomCommandLineWorkingDir() {
        return preferences.get("productAccess.customCommandLine.workingDir", "");
    }

    public void setCustomCommandLineWorkingDir(String dir) {
        preferences.put("productAccess.customCommandLine.workingDir", dir);
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
        preferences.put("productAccess.localPaths", String.join(File.pathSeparator, url));
    }

    private String getDefaultDefaultUrl() {
        CBIRSession instance = CBIRSession.getInstance();
        PFAApplicationDescriptor applicationDescriptor = instance.getApplicationDescriptor();
        String defaultValue = "";
        if (applicationDescriptor != null ) {
            defaultValue = applicationDescriptor.getDefaultDataAccessPattern();
        }
        return defaultValue;
    }
}
