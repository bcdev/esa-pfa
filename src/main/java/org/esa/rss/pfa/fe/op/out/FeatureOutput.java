package org.esa.rss.pfa.fe.op.out;

import org.esa.rss.pfa.fe.op.Feature;
import org.esa.rss.pfa.fe.op.Patch;

import java.io.IOException;

/**
 * @author Norman Fomferra
 */
public interface FeatureOutput {
    String writeFeature(Patch patch, Feature feature, String dirPath) throws IOException;
}
