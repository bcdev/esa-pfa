package org.esa.rss.pfa.fe.op;

import org.esa.beam.framework.datamodel.Product;

import java.io.File;
import java.io.IOException;

/**
 * @author Norman Fomferra
 */
public class DefaultFeatureOutputFactory implements FeatureOutputFactory {
    private File targetDir;

    @Override
    public void setTargetPath(String path) {
        targetDir = new File(path);
    }

    @Override
    public FeatureOutput createFeatureOutput(Product sourceProduct) throws IOException {
        if (!targetDir.exists()) {
            throw new IOException(String.format("Target directory does not exist: '%s'", targetDir));
        }
        final File fexTargetDir = new File(targetDir, sourceProduct.getName() + ".fex");
        if (!fexTargetDir.mkdir()) {
            throw new IOException(String.format("Failed to create directory '%s'", fexTargetDir));
        }
        return new DefaultFeatureOutput(fexTargetDir);
    }
}
