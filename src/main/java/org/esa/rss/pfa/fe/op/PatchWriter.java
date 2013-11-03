package org.esa.rss.pfa.fe.op;

import org.esa.beam.framework.datamodel.Product;

import java.io.Closeable;
import java.io.IOException;

/**
* @author Norman Fomferra
*/
public interface PatchWriter extends PatchOutput, Closeable {
    void initialize(Product sourceProduct, String[] labelNames, FeatureType... featureTypes) throws IOException;
}
