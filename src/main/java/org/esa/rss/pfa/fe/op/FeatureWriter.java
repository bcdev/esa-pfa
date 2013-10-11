package org.esa.rss.pfa.fe.op;

import java.io.IOException;

/**
 * @author Norman Fomferra
 */
public interface FeatureWriter {
    String writeFeature(Feature feature, String dirPath) throws IOException;
}
