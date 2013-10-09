package org.esa.rss.pfa.fe.op;

import org.esa.beam.framework.datamodel.Product;

import java.io.IOException;

/**
* @author Norman Fomferra
*/
public interface FeatureOutput {
    void writeMetadata(FeatureType... featureTypes) throws IOException;
    void writePatchFeatures(int patchX, int patchY, Product product, Feature... features) throws IOException;
    void close()throws IOException;
}
