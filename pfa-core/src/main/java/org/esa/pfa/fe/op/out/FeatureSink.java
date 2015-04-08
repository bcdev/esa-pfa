package org.esa.pfa.fe.op.out;

import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.Patch;

import java.io.IOException;
import java.nio.file.Path;

/**
 * @author Norman Fomferra
 */
public interface FeatureSink {
    String writeFeature(Feature feature, Path targetDirPath) throws IOException;
}