/*
 * Copyright (c) 2013. Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.rss.pfa.fe.op;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.datamodel.Stx;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.experimental.Output;
import org.esa.beam.jai.ImageManager;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.logging.BeamLogManager;

import javax.media.jai.JAI;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.HashMap;

/**
 * Abstract feature extraction operator. Features are extracted from product "patches".
 *
 * @author Norman Fomferra
 */
@OperatorMetadata(alias = "FexOp", version = "1.0")
public abstract class FexOperator extends Operator implements Output {

    public static final AttributeType[] STX_ATTRIBUTE_TYPES = new AttributeType[]{
            new AttributeType("mean", "Mean value of valid feature pixels", Double.class),
            new AttributeType("stdev", "Standard deviation of valid feature pixels", Double.class),
            new AttributeType("cvar", "Coefficient of variation of valid feature pixels", Double.class),
            new AttributeType("min", "Minimim value of valid feature pixels", Double.class),
            new AttributeType("max", "Maximum value of valid feature pixels", Double.class),
            new AttributeType("median", "Median value of valid feature pixels (estimation from 512-bin histogram)", Double.class),
            new AttributeType("p10", "The thresholds such that 10% of the sample values are below the threshold", Double.class),
            new AttributeType("p90", "The thresholds such that 90% of the sample values are below the threshold", Double.class),
            new AttributeType("entropy", "The histogram entropy defined to be the negation of the sum of the products of the probability associated with each bin with the base-2 log of the probability.", Double.class),
            new AttributeType("count", "Sample count (number of valid feature pixels)", Integer.class),
    };
    public static final int DEFAULT_PATCH_SIZE = 200;

    @SourceProduct
    protected Product sourceProduct;

    @Parameter(defaultValue = DEFAULT_PATCH_SIZE + "")
    protected int patchWidth;

    @Parameter(defaultValue = DEFAULT_PATCH_SIZE + "")
    protected int patchHeight;

    @Parameter(description = "The path where features will be extracted to")
    protected String targetPath;

    @Parameter(defaultValue = "false", description = "Disposes all global image caches after a patch has been completed")
    protected boolean disposeGlobalCaches;

    @Parameter(defaultValue = "false")
    protected boolean overwriteMode;

    @Parameter(defaultValue = "false")
    protected boolean skipFeaturesOutput;

    @Parameter(defaultValue = "false")
    protected boolean skipQuicklookOutput;

    @Parameter(defaultValue = "false")
    protected boolean skipProductOutput;

    @Parameter(defaultValue = "org.esa.rss.pfa.fe.op.DefaultFeatureOutputFactory")
    private String featureOutputFactoryClassName;

    private transient FeatureOutputFactory featureOutputFactory;

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public void setOverwriteMode(boolean overwriteMode) {
        this.overwriteMode = overwriteMode;
    }

    public void setSkipFeaturesOutput(boolean skipFeaturesOutput) {
        this.skipFeaturesOutput = skipFeaturesOutput;
    }

    public void setSkipProductOutput(boolean skipProductOutput) {
        this.skipProductOutput = skipProductOutput;
    }

    public void setSkipQuicklookOutput(boolean skipQuicklookOutput) {
        this.skipQuicklookOutput = skipQuicklookOutput;
    }

    public void setPatchWidth(int patchWidth) {
        this.patchWidth = patchWidth;
    }

    public void setPatchHeight(int patchHeight) {
        this.patchHeight = patchHeight;
    }

    public void setFeatureOutputFactory(FeatureOutputFactory featureOutputFactory) {
        this.featureOutputFactory = featureOutputFactory;
    }

    protected Feature createStxFeature(FeatureType featureType, Product product) {
        RasterDataNode band = product.getBand(featureType.getName());
        return createStxFeature(featureType, band);
    }

    protected Feature createStxFeature(FeatureType featureType, RasterDataNode rasterDataNode) {
        Guardian.assertSame("invalid feature type", featureType.getAttributeTypes(), STX_ATTRIBUTE_TYPES);
        final Stx stx = rasterDataNode.getStx(true, ProgressMonitor.NULL);
        return new Feature(featureType,
                           null,
                           stx.getMean(),
                           stx.getStandardDeviation(),
                           stx.getStandardDeviation() / stx.getMean(),
                           stx.getMinimum(),
                           stx.getMaximum(),
                           stx.getMedian(),
                           stx.getHistogram().getPTileThreshold(0.1)[0],
                           stx.getHistogram().getPTileThreshold(0.9)[0],
                           stx.getHistogram().getEntropy()[0],
                           stx.getSampleCount());
    }

    protected abstract FeatureType[] getFeatureTypes();

    protected abstract boolean processPatch(Patch patch, PatchSink sink) throws IOException;

    @Override
    public void initialize() throws OperatorException {

        if (featureOutputFactory == null) {
            initFeatureOutputFactory();
        }

        // todo - nf20131010 - make 'outputProperties' an operator parameter so that we can have FeatureOutputFactory-specific properties (e.g. from Hadoop job requests)
        HashMap<String, String> outputProperties = new HashMap<String, String>();
        outputProperties.put(FeatureOutputFactory.PROPERTY_TARGET_PATH, targetPath);
        outputProperties.put(FeatureOutputFactory.PROPERTY_OVERWRITE_MODE, overwriteMode + "");
        outputProperties.put(FeatureOutputFactory.PROPERTY_SKIP_QUICKLOOK_OUTPUT, skipQuicklookOutput + "");
        outputProperties.put(FeatureOutputFactory.PROPERTY_SKIP_PRODUCT_OUTPUT, skipProductOutput + "");
        outputProperties.put(FeatureOutputFactory.PROPERTY_SKIP_FEATURE_OUTPUT, skipFeaturesOutput + "");
        featureOutputFactory.configure(outputProperties);

        if (overwriteMode) {
            getLogger().warning("FexOperator: Overwrite mode is on.");
        }
        if (skipFeaturesOutput) {
            getLogger().warning("FexOperator: Feature output skipped.");
        }
        if (skipProductOutput) {
            getLogger().warning("FexOperator: Product output skipped.");
        }
        if (skipQuicklookOutput) {
            getLogger().warning("FexOperator: RGB image output skipped.");
        }

        setTargetProduct(sourceProduct);

        int productSizeX = sourceProduct.getSceneRasterWidth();
        int productSizeY = sourceProduct.getSceneRasterHeight();
        int patchCountX = (productSizeX + patchWidth - 1) / patchWidth;
        int patchCountY = (productSizeY + patchHeight - 1) / patchHeight;

        try {
            run(patchCountX, patchCountY);
        } catch (IOException e) {
            throw new OperatorException(e);
        }
    }

    private void run(int patchCountX, int patchCountY) throws IOException {
        FeatureOutput featureOutput = featureOutputFactory.createFeatureOutput(sourceProduct);
        FeatureType[] featureTypes = getFeatureTypes();
        featureOutput.initialize(getSourceProduct(), featureTypes);

        PatchSinkImpl sink = new PatchSinkImpl(featureOutput);

        long t0 = System.currentTimeMillis();
        for (int patchY = 0; patchY < patchCountY; patchY++) {
            for (int patchX = 0; patchX < patchCountX; patchX++) {
                long t1 = System.currentTimeMillis();

                Rectangle patchRegion = createSubsetRegion(sourceProduct, patchY, patchX);
                Product patchProduct = createSubset(sourceProduct, patchRegion);
                patchProduct.setName("patch");

                Patch patch = new Patch(patchX, patchY, patchRegion, patchProduct);
                processPatch(patch, sink);

                patchProduct.dispose();

                if (disposeGlobalCaches) {
                    ImageManager.getInstance().dispose();
                    JAI.getDefaultInstance().getTileCache().flush();
                }

                logProgress(t0, t1, patchCountX, patchCountY, patchX, patchY);
            }
        }

        featureOutput.close();

        logCompletion(t0, patchCountX, patchCountY);
    }

    private void logCompletion(long t0, int patchCountX, int patchCountY) {
        int patchCount = patchCountX * patchCountY;
        double totalSec = (System.currentTimeMillis() - t0) / 1000.0;
        BeamLogManager.getSystemLogger().info(String.format("Completed %d patches in %.1f sec", patchCount, totalSec));
    }

    private void logProgress(long t0, long t1, int patchCountX, int patchCountY, int patchX, int patchY) {
        int patchCount = patchCountX * patchCountY;
        int patchIndex = patchY * patchCountX + patchX;
        int progress = (int) (100.0 * patchIndex / (patchCount - 1.0) + 0.5);
        long t2 = System.currentTimeMillis();
        double patchSec = (t2 - t1) / 1000.0;
        double patchSecAvg = (t2 - t0) / 1000.0 / (patchIndex + 1.0);
        double totalSec = patchSecAvg * (patchCount - patchIndex - 1);
        BeamLogManager.getSystemLogger().info(String.format("Completed patch %d of %d patches (%d%% done) in %.1f sec. Still %.1f sec left to completion.",
                                                            patchIndex + 1, patchCount, progress, patchSec, totalSec));
    }

    private void initFeatureOutputFactory() {
        try {
            Class<?> featureOutputFactoryClass = getClass().getClassLoader().loadClass(featureOutputFactoryClassName);
            this.featureOutputFactory = (FeatureOutputFactory) featureOutputFactoryClass.newInstance();
        } catch (ClassNotFoundException e) {
            throw new OperatorException(e);
        } catch (InstantiationException e) {
            throw new OperatorException(e);
        } catch (IllegalAccessException e) {
            throw new OperatorException(e);
        }
    }

    private Rectangle createSubsetRegion(Product sourceProduct, int tileY, int tileX) {
        final int productSizeX = sourceProduct.getSceneRasterWidth();
        final int productSizeY = sourceProduct.getSceneRasterHeight();
        final Rectangle sceneBoundary = new Rectangle(0, 0, productSizeX, productSizeY);
        final int x = tileX * patchWidth;
        final int y = tileY * patchHeight;
        return new Rectangle(x, y, patchWidth, patchHeight).intersection(sceneBoundary);
    }

    private Product createSubset(Product sourceProduct, Rectangle subsetRegion) {
        final HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("region", subsetRegion);
        parameters.put("copyMetadata", false);
        return GPF.createProduct("Subset", parameters, sourceProduct);
    }

    public static final class Patch {
        final int patchX;
        final int patchY;
        final Rectangle patchRegion;
        final Product patchProduct;


        public Patch(int patchX, int patchY, Rectangle patchRegion, Product patchProduct) {
            this.patchX = patchX;
            this.patchY = patchY;
            this.patchRegion = patchRegion;
            this.patchProduct = patchProduct;
        }

        public int getPatchX() {
            return patchX;
        }

        public int getPatchY() {
            return patchY;
        }

        public Rectangle getPatchRegion() {
            return patchRegion;
        }

        public Product getPatchProduct() {
            return patchProduct;
        }
    }

    public interface PatchSink {

        void writePatchFeatures(Patch patch, Feature... features) throws IOException;
    }

    public class PatchSinkImpl implements PatchSink {
        private final FeatureOutput featureOutput;

        public PatchSinkImpl(FeatureOutput featureOutput) {
            this.featureOutput = featureOutput;
        }

        @Override
        public void writePatchFeatures(Patch patch, Feature... features) throws IOException {
            featureOutput.writePatchFeatures(patch.getPatchX(), patch.getPatchY(), patch.getPatchProduct(), features);
        }
    }
}
