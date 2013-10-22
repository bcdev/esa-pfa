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

package org.esa.rss.pfa.fe;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glevel.support.AbstractMultiLevelSource;
import com.bc.ceres.glevel.support.DefaultMultiLevelImage;
import org.esa.beam.classif.CcNnHsOp;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.ImageInfo;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.RGBChannelDef;
import org.esa.beam.framework.datamodel.Stx;
import org.esa.beam.framework.datamodel.StxFactory;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.main.GPT;
import org.esa.beam.jai.ImageManager;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.meris.radiometry.equalization.ReprocessingVersion;
import org.esa.beam.util.ProductUtils;
import org.esa.rss.pfa.fe.op.Feature;
import org.esa.rss.pfa.fe.op.FeatureType;
import org.esa.rss.pfa.fe.op.FexOperator;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.DataBufferFloat;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * An operator for extracting algal bloom features.
 *
 * @author Norman Fomferra
 * @author Ralf Quast
 */
@OperatorMetadata(alias = "AlgalBloomFex", version = "1.1")
public class AlgalBloomFexOperator extends FexOperator {

    public static void main(String[] args) {
        System.setProperty("beam.reader.tileWidth", String.valueOf(DEFAULT_PATCH_SIZE));
        System.setProperty("beam.reader.tileHeight", String.valueOf(DEFAULT_PATCH_SIZE));
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
        try {
            GPT.main(appendArgs("AlgalBloomFex", args));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }


    public static final String FEX_VALID_MASK = "NOT (l1_flags.INVALID OR l1_flags.LAND_OCEAN)";

    public static final String FEX_CLOUD_MASK_1_NAME = "fex_cloud_1";
    public static final String FEX_CLOUD_MASK_1_VALUE = "inrange(1.0,radiance_2/radiance_1,radiance_3/radiance_1,radiance_4/radiance_1,radiance_5/radiance_1,radiance_6/radiance_1,radiance_7/radiance_1,radiance_8/radiance_1,radiance_9/radiance_1,radiance_10/radiance_1,radiance_11/radiance_1,radiance_12/radiance_1,radiance_13/radiance_1,radiance_14/radiance_1,radiance_15/radiance_1,0.9825,0.8912440298255327,0.7053624619190219,0.632554865136358,0.4575615968608662,0.2179392690391927,0.16292546567267474,0.14507751149298348,0.11298530962868607,0.07717095822527878,0.026874729262769237,0.0644775478429025,0.032459522893688454,0.02857828581108522,0.019437950982025554,1.0175,1.1763689248192413,1.273366916408814,1.2648073865199438,1.203150647878767,1.131659556515563,1.119413895149373,1.0961100428051727,0.9982435054213645,1.1191481235570606,0.5293549869787241,1.0707186507909972,0.9215112851384355,0.8887876651578702,0.6442855617909731)";

    public static final String FEX_CLOUD_MASK_2_NAME = "fex_cloud_2";
    public static final String FEX_CLOUD_MASK_2_VALUE = "cl_wat_3_val > 1.8";

    public static final String FEX_VALID_MASK_NAME = "fex_roi";

    public static final String FEX_COAST_DIST_PRODUCT_PATH = "auxdata/coast_dist_2880.dim";

    @Parameter(defaultValue = "0.2")
    private double minValidPixelRatio;

    private transient float[] coastDistData;
    private transient int coastDistWidth;
    private transient int coastDistHeight;

    public static final FeatureType[] FEATURE_TYPES = new FeatureType[]{
            /*00*/ new FeatureType("patch", "Patch product", Product.class),
            /*01*/ new FeatureType("patch_ql", "Quicklook for patch product", RenderedImage.class),
            /*02*/ new FeatureType("patch_ql_masked", "Masked quicklook for patch product", RenderedImage.class),
            /*03*/ new FeatureType("mci", "Maximum Chlorophyll Index", STX_ATTRIBUTE_TYPES),
            /*04*/ new FeatureType("flh", "Fluorescence Line Height", STX_ATTRIBUTE_TYPES),
            /*05*/ new FeatureType("coast_dist", "Distance from next coast pixel (km)", STX_ATTRIBUTE_TYPES),
            /*06*/ new FeatureType("valid_pixels", "Ratio of valid pixels in patch", Double.class),
            /*07*/ new FeatureType("connected_pixels", "Ratio of connected pixels in patch", Double.class),
            /*08*/ new FeatureType("fractal_index", "Fractal index estimation", Double.class),
            /*09*/ new FeatureType("p11", "p11", Double.class),
            /*10*/ new FeatureType("n11_n10_n11", "n11 / (n10 + n11)", Double.class),
            /*11*/ new FeatureType("n11_n10", "n11 / n10", Double.class),
            /*12*/ new FeatureType("clumpiness", "Clumpiness index", Double.class),
    };

    @Override
    protected FeatureType[] getFeatureTypes() {
        return FEATURE_TYPES;
    }

    @Override
    public void initialize() throws OperatorException {

        Product coastDistProduct;
        try {
            coastDistProduct = ProductIO.readProduct(FEX_COAST_DIST_PRODUCT_PATH);
        } catch (IOException e) {
            throw new OperatorException(e);
        }

        // todo - regenerate a better costDist dataset. The current one has a cutoff at ~800 nm
        final Band coastDistance = coastDistProduct.addBand("coast_dist_nm_cleaned",
                                                            "coast_dist_nm > 300.0 ? 300.0 : coast_dist_nm");
        coastDistWidth = coastDistProduct.getSceneRasterWidth();
        coastDistHeight = coastDistProduct.getSceneRasterHeight();
        coastDistData = ((DataBufferFloat) coastDistance.getSourceImage().getData().getDataBuffer()).getData();
        coastDistProduct.dispose();

        super.initialize();
    }

    @Override
    protected Feature[] extractPatchFeatures(int patchX, int patchY, Rectangle subsetRegion, Product patchProduct) {
        if (skipFeaturesOutput && skipQuicklookOutput && skipProductOutput) {
            return null;
        }

        int numPixelsRequired = patchWidth * patchHeight;
        int numPixelsTotal = patchProduct.getSceneRasterWidth() * patchProduct.getSceneRasterHeight();

        double patchPixelRatio = numPixelsTotal / (double) numPixelsRequired;
        if (patchPixelRatio < minValidPixelRatio) {
            getLogger().warning(String.format("Rejected patch x%dy%d, patchPixelRatio=%f%%", patchX, patchY, patchPixelRatio * 100));
            return null;
        }

        final Product featureProduct = createCorrectedProduct(patchProduct);
        addCloudMask(featureProduct);
        addValidMask(featureProduct);
        final Band mciBand = addMciBand(featureProduct);

        final StxFactory stxFactory = new StxFactory();
        stxFactory.withRoiMask(featureProduct.getMaskGroup().get(FEX_VALID_MASK_NAME));
        final Stx stx = stxFactory.create(mciBand, ProgressMonitor.NULL);

        double validPixelRatio = stx.getSampleCount() / (double) numPixelsRequired;
        if (validPixelRatio < minValidPixelRatio) {
            getLogger().warning(String.format("Rejected patch x%dy%d, validPixelRatio=%f%%", patchX, patchY, validPixelRatio * 100));
            return null;
        }

        final Product reflectanceProduct = createReflectanceProduct(featureProduct);
        for (final String bandName : reflectanceProduct.getBandNames()) {
            if (bandName.startsWith("reflec")) {
                ProductUtils.copyBand(bandName, reflectanceProduct, featureProduct, true);
            }
        }
        addFlhBand(featureProduct);

        // todo - dirty code from NF, clean up
        final Band coastDistBand = featureProduct.addBand("coast_dist", ProductData.TYPE_FLOAT32);
        final DefaultMultiLevelImage coastDistImage = new DefaultMultiLevelImage(
                new AbstractMultiLevelSource(ImageManager.getMultiLevelModel(coastDistBand)) {
                    @Override
                    protected RenderedImage createImage(int level) {
                        return new WorldDataOpImage(featureProduct.getGeoCoding(), coastDistBand,
                                                    ResolutionLevel.create(getModel(), level),
                                                    coastDistWidth, coastDistHeight, coastDistData);
                    }
                });
        coastDistBand.setSourceImage(coastDistImage);


        Mask mask = featureProduct.getMaskGroup().get(FEX_VALID_MASK_NAME);
        AggregationMetrics aggregationMetrics = AggregationMetrics.compute(mask);
        ConnectivityMetric connectivityMetric = ConnectivityMetric.compute(mask);

        RenderedImage[] images = createReflectanceRgbImages(featureProduct, "NOT l1_flags.INVALID", FEX_VALID_MASK_NAME);

        return new Feature[]{
                new Feature(FEATURE_TYPES[0], featureProduct),
                new Feature(FEATURE_TYPES[1], images[0]),
                new Feature(FEATURE_TYPES[2], images[1]),
                createFeature(FEATURE_TYPES[3], featureProduct),
                createFeature(FEATURE_TYPES[4], featureProduct),
                createFeature(FEATURE_TYPES[5], featureProduct),
                new Feature(FEATURE_TYPES[6], validPixelRatio),
                new Feature(FEATURE_TYPES[7], connectivityMetric.connectionRatio),
                new Feature(FEATURE_TYPES[8], connectivityMetric.fractalIndex),
                new Feature(FEATURE_TYPES[9], aggregationMetrics.p11),
                new Feature(FEATURE_TYPES[10], (double) aggregationMetrics.n11 / (double) (aggregationMetrics.n10 + aggregationMetrics.n11)),
                new Feature(FEATURE_TYPES[11], (double) aggregationMetrics.n11 / (double) aggregationMetrics.n10),
                new Feature(FEATURE_TYPES[12], aggregationMetrics.clumpiness),
        };
    }


    private Product createReflectanceProduct(Product sourceProduct) {
        final HashMap<String, Object> radiometryParameters = new HashMap<String, Object>();
        radiometryParameters.put("doCalibration", false);
        radiometryParameters.put("doSmile", false);
        radiometryParameters.put("doEqualization", false);
        radiometryParameters.put("doRadToRefl", true);
        return GPF.createProduct("Meris.CorrectRadiometry", radiometryParameters, sourceProduct);
    }

    private void addValidMask(Product featureProduct) {
        final String expression = String.format("(%s) AND NOT (%s)", FEX_VALID_MASK, FEX_CLOUD_MASK_2_NAME);
        featureProduct.addMask(FEX_VALID_MASK_NAME, expression, "ROI for pixels used for the feature extraction", Color.green, 0.5);
    }

    private void addCloudMask(Product product) {

        CcNnHsOp ccNnHsOp = new CcNnHsOp();
        ccNnHsOp.setSourceProduct(product);
        ccNnHsOp.setValidPixelExpression(FEX_VALID_MASK);
        ccNnHsOp.setAlgorithmName(CcNnHsOp.ALGORITHM_2013_05_09);
        Product cloudProduct = ccNnHsOp.getTargetProduct();

        ProductUtils.copyBand("cl_wat_3_val", cloudProduct, product, true);

        product.addMask(FEX_CLOUD_MASK_1_NAME, FEX_CLOUD_MASK_1_VALUE, "Special MERIS L1B cloud mask for PFA (magic wand)", Color.YELLOW,
                        0.5);
        product.addMask(FEX_CLOUD_MASK_2_NAME, FEX_CLOUD_MASK_2_VALUE, "Special MERIS L1B cloud mask for PFA (Schiller NN)", Color.ORANGE,
                        0.5);
    }

    private Band addMciBand(Product sourceProduct) {
        final Band l1 = sourceProduct.getBand("radiance_8");
        final Band l2 = sourceProduct.getBand("radiance_9");
        final Band l3 = sourceProduct.getBand("radiance_10");

        final double lambda1 = l1.getSpectralWavelength();
        final double lambda2 = l2.getSpectralWavelength();
        final double lambda3 = l3.getSpectralWavelength();
        final double factor = (lambda2 - lambda1) / (lambda3 - lambda1);
        final double cloudCorrectionFactor = 1.005;

        final Band mci = sourceProduct.addBand("mci",
                                               String.format("%s - %s * (%s - (%s - %s) * %s)",
                                                             l2.getName(),
                                                             cloudCorrectionFactor,
                                                             l1.getName(),
                                                             l3.getName(),
                                                             l1.getName(), factor));
        mci.setValidPixelExpression(FEX_VALID_MASK_NAME);
        return mci;
    }

    private void addFlhBand(Product sourceProduct) {
        final Band l1 = sourceProduct.getBand("reflec_7");
        final Band l2 = sourceProduct.getBand("reflec_8");
        final Band l3 = sourceProduct.getBand("reflec_9");

        final double lambda1 = l1.getSpectralWavelength();
        final double lambda2 = l2.getSpectralWavelength();
        final double lambda3 = l3.getSpectralWavelength();
        final double factor = (lambda2 - lambda1) / (lambda3 - lambda1);
        final double cloudCorrectionFactor = 1.005;

        final Band flh = sourceProduct.addBand("flh",
                                               String.format("%s - %s * (%s - (%s - %s) * %s)",
                                                             l2.getName(),
                                                             cloudCorrectionFactor,
                                                             l1.getName(),
                                                             l3.getName(),
                                                             l1.getName(), factor));
        flh.setValidPixelExpression(FEX_VALID_MASK_NAME);
    }

    private Product createCorrectedProduct(Product sourceProduct) {
        final HashMap<String, Object> radiometryParameters = new HashMap<String, Object>();
        radiometryParameters.put("doCalibration", false);
        radiometryParameters.put("doSmile", true);
        radiometryParameters.put("doEqualization", true);
        radiometryParameters.put("reproVersion", ReprocessingVersion.REPROCESSING_3);
        radiometryParameters.put("doRadToRefl", false);
        return GPF.createProduct("Meris.CorrectRadiometry", radiometryParameters, sourceProduct);
    }

    private Feature createFeature(FeatureType featureType, Product product) {
        final StxFactory stxFactory = new StxFactory();
        stxFactory.withRoiMask(product.getMaskGroup().get(FEX_VALID_MASK_NAME));
        final Stx stx = stxFactory.create(product.getBand(featureType.getName()), ProgressMonitor.NULL);
        return new Feature(featureType, null,
                           stx.getMean(),
                           stx.getMedian(),
                           stx.getMinimum(),
                           stx.getMaximum(),
                           stx.getStandardDeviation(),
                           stx.getSampleCount());
    }

    private RenderedImage[] createReflectanceRgbImages(Product product, String... validMasks) {
        double minR = -2.0;
        double maxR = -1.0;

        double minG = -2.0;
        double maxG = -1.0;

        double minB = -1.5;
        double maxB = -0.5;

        Band r = product.addBand("virtual_red", "log(0.05 + 0.35 * reflec_2 + 0.60 * reflec_5 + reflec_6 + 0.13 * reflec_7)");
        Band g = product.addBand("virtual_green", "log(0.05 + 0.21 * reflec_3 + 0.50 * reflec_4 + reflec_5 + 0.38 * reflec_6)");
        Band b = product.addBand("virtual_blue", "log(0.05 + 0.21 * reflec_1 + 1.75 * reflec_2 + 0.47 * reflec_3 + 0.16 * reflec_4)");

        RenderedImage[] images = new RenderedImage[validMasks.length];
        for (int i = 0; i < validMasks.length; i++) {
            images[i] = getRenderedImageDDD(validMasks[i], minR, maxR, 1.2, minG, maxG, 1.2, minB, maxB, 1.5, r, g, b);
        }

        product.removeBand(r);
        product.removeBand(g);
        product.removeBand(b);

        r.dispose();
        g.dispose();
        b.dispose();

        return images;
    }

    private RenderedImage getRenderedImageDDD(String expressionA, double minR, double maxR, double gammaR, double minG, double maxG, double gammaG, double minB, double maxB, double gammaB, Band r, Band g, Band b) {
        r.setValidPixelExpression(expressionA);
        r.setNoDataValue(Double.NaN);
        r.setNoDataValueUsed(true);

        RGBChannelDef rgbChannelDef = new RGBChannelDef(new String[]{r.getName(), g.getName(), b.getName()});
        rgbChannelDef.setMinDisplaySample(0, minR);
        rgbChannelDef.setMaxDisplaySample(0, maxR);
        rgbChannelDef.setGamma(0, gammaR);

        rgbChannelDef.setMinDisplaySample(1, minG);
        rgbChannelDef.setMaxDisplaySample(1, maxG);
        rgbChannelDef.setGamma(1, gammaG);

        rgbChannelDef.setMinDisplaySample(2, minB);
        rgbChannelDef.setMaxDisplaySample(2, maxB);
        rgbChannelDef.setGamma(2, gammaB);

        return ImageManager.getInstance().createColoredBandImage(new Band[]{r, g, b}, new ImageInfo(rgbChannelDef), 0);
    }


    private static String[] appendArgs(String algalBloomFex1, String[] args) {
        List<String> algalBloomFex = new ArrayList<String>(Arrays.asList(algalBloomFex1));
        algalBloomFex.addAll(Arrays.asList(args));
        return algalBloomFex.toArray(new String[0]);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AlgalBloomFexOperator.class);
        }
    }

}
