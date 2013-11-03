package org.esa.rss.pfa.fe.op;

import java.io.IOException;

/**
 * @author Norman Fomferra
 */
public interface FeatureOutput {
    String writeFeature(Patch patch, Feature feature, String dirPath) throws IOException;
}
