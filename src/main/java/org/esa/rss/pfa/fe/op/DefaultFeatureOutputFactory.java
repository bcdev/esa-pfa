package org.esa.rss.pfa.fe.op;

import org.esa.beam.framework.datamodel.Product;

import java.io.IOException;

/**
 * @author Norman Fomferra
 */
public class DefaultFeatureOutputFactory extends FeatureOutputFactory {

    @Override
    public FeatureOutput createFeatureOutput(Product sourceProduct) throws IOException {
        return new DefaultFeatureOutput(this, sourceProduct.getName());
    }
}
