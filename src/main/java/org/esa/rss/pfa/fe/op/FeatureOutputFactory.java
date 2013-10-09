package org.esa.rss.pfa.fe.op;

import org.esa.beam.framework.datamodel.Product;

import java.io.IOException;

/**
* @author Norman Fomferra
*/
public interface FeatureOutputFactory {

   void setTargetPath(String targetPath);

   FeatureOutput createFeatureOutput(Product sourceProduct) throws IOException;
}
