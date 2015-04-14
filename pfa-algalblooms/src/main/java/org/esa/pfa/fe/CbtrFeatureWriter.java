package org.esa.pfa.fe;

import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.gpf.OperatorSpi;
import org.esa.snap.framework.gpf.annotations.OperatorMetadata;
import org.esa.snap.framework.gpf.annotations.Parameter;
import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.FeatureWriter;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.fe.op.out.PatchSink;

import java.io.IOException;

/**
 * @author Ralf Quast
 */
@OperatorMetadata(alias = "CbtrFeatureWriter", version = "0.1", autoWriteDisabled = true)
public class CbtrFeatureWriter extends FeatureWriter {

    @Parameter(defaultValue = "0.0")
    private double acceptanceThreshold;

    private FeatureType[] featureTypes;

    @Override
    protected FeatureType[] getFeatureTypes() {
        if (featureTypes == null) {
            featureTypes = new FeatureType[]{
                    new FeatureType("patch", "Patch product", Product.class),
            };
        }
        return featureTypes;
    }

    @Override
    protected boolean processPatch(Patch patch, PatchSink sink) throws IOException {
        if (skipFeaturesOutput && skipQuicklookOutput && skipProductOutput) {
            return false;
        }

        // TODO: use PatchGrid somehow and somewhere

        if (!accept(patch)) {
            return false;
        }

        final Product patchProduct = patch.getPatchProduct();
        final Feature[] features = {
                new Feature(featureTypes[0], patchProduct),
        };

        sink.writePatch(patch, features);

        return true;
    }

    // simulates filtering
    private boolean accept(Patch patch) {
        return Math.random() > acceptanceThreshold;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CbtrFeatureWriter.class);
        }
    }

}
