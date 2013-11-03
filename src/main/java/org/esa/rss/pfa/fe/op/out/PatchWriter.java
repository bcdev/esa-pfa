package org.esa.rss.pfa.fe.op.out;

import org.esa.beam.framework.datamodel.Product;
import org.esa.rss.pfa.fe.op.FeatureType;

import java.io.Closeable;
import java.io.IOException;

/**
* @author Norman Fomferra
*/
public interface PatchWriter extends PatchOutput, Closeable {
    void initialize(Product sourceProduct, String[] labelNames, FeatureType... featureTypes) throws IOException;
}
