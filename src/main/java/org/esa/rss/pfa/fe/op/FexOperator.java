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

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.experimental.Output;
import org.esa.beam.jai.ImageManager;
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
            new AttributeType("median", "Median value of valid feature pixels (estimation from 512-bin histogram)", Double.class),
            new AttributeType("min", "Minimim value of valid feature pixels", Double.class),
            new AttributeType("max", "Maximum value of valid feature pixels", Double.class),
            new AttributeType("stdev", "Standard deviation of valid feature pixels", Double.class),
            //new AttributeType("cvar", "Coefficient of variation of valid feature pixels", Double.class),
            new AttributeType("count", "Number of valid feature pixels", Integer.class),
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

    protected abstract FeatureType[] getFeatureTypes();

    protected abstract Feature[] extractPatchFeatures(int patchX, int patchY, Rectangle patchRegion, Product patchProduct);

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
            System.out.println("Warning: FexOp: Overwrite mode is on.");
        }
        if (skipFeaturesOutput) {
            System.out.println("Warning: FexOp: Feature output skipped.");
        }
        if (skipProductOutput) {
            System.out.println("Warning: FexOp: Product output skipped.");
        }
        if (skipQuicklookOutput) {
            System.out.println("Warning: FexOp: RGB image output skipped.");
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

        double patchSecAvg = 0;

        long t0 = System.currentTimeMillis();
        for (int patchY = 0; patchY < patchCountY; patchY++) {
            for (int patchX = 0; patchX < patchCountX; patchX++) {
                long t1 = System.currentTimeMillis();

                Rectangle patchRegion = createSubsetRegion(sourceProduct, patchY, patchX);
                Product patchProduct = createSubset(sourceProduct, patchRegion);
                patchProduct.setName("patch");

                Feature[] features = extractPatchFeatures(patchX, patchY, patchRegion, patchProduct);
                if (features != null) {
                    featureOutput.writePatchFeatures(patchX, patchY, patchProduct, features);
                    disposeProducts(features);
                }

                patchProduct.dispose();

                if (disposeGlobalCaches) {
                    ImageManager.getInstance().dispose();
                    JAI.getDefaultInstance().getTileCache().flush();
                }

                patchSecAvg = logProgress(t1, patchCountX, patchCountY, patchX, patchY, patchSecAvg);
            }
        }

        featureOutput.close();

        logCompletion(t0, patchCountX, patchCountY);
    }

    private void disposeProducts(Feature[] features) {
        for (Feature feature : features) {
            if (feature.getValue() instanceof Product) {
                Product product = (Product) feature.getValue();
                product.dispose();
            }
        }
    }

    private void logCompletion(long t0, int patchCountX, int patchCountY) {
        int patchCount = patchCountX * patchCountY;
        double totalSec = (System.currentTimeMillis() - t0) / 1000.0;
        BeamLogManager.getSystemLogger().info(String.format("Completed %d patches in %.1f sec", patchCount, totalSec));
    }

    private double logProgress(long t0, int patchCountX, int patchCountY, int patchX, int patchY, double patchSecAvg) {
        int patchCount = patchCountX * patchCountY;
        int patchIndex = patchY * patchCountX + patchX;
        int progress = (int) (100.0 * patchIndex / (patchCount - 1.0) + 0.5);
        double patchSec = (System.currentTimeMillis() - t0) / 1000.0;
        patchSecAvg = 0.5 * (patchSecAvg + patchSec);
        double totalSec = (patchCount - (patchIndex + 1)) * patchSecAvg;
        BeamLogManager.getSystemLogger().info(String.format("Completed patch %d of %d patches (%d%% done) in %.1f sec. Still %.1f sec left to completion.",
                                                            patchIndex + 1, patchCount, progress, patchSec, totalSec));
        return patchSecAvg;
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
}
