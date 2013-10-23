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
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
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
import java.awt.image.RenderedImage;
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

    @TargetProduct
    private Product targetProduct;

    private Product reflectanceProduct;
    private Product cloudProduct;
    private Band mciBand;
    private Band flhBand;

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

    public static final String FEX_ROI_MASK_NAME = "fex_roi";

    public static final String FEX_COAST_DIST_PRODUCT_PATH = "auxdata/coast_dist_2880.dim";

    @Parameter(defaultValue = "0.2")
    private double minValidPixelRatio;

    private transient float[] coastDistMapData;
    private transient int coastDistanceMapWidth;
    private transient int coastDistanceMapHeight;

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
        Product coastDistProduct = null;
        try {
            coastDistProduct = ProductIO.readProduct(FEX_COAST_DIST_PRODUCT_PATH);

            // TODO - regenerate a better costDist dataset. The current one has a cutoff at ~800 nm
            final Band coastDistance = coastDistProduct.addBand("coast_dist_nm_cleaned",
                                                                "coast_dist_nm > 300.0 ? 300.0 : coast_dist_nm");
            coastDistanceMapWidth = coastDistProduct.getSceneRasterWidth();
            coastDistanceMapHeight = coastDistProduct.getSceneRasterHeight();
            coastDistMapData = new float[coastDistanceMapWidth * coastDistanceMapHeight];
            coastDistance.getSourceImage().getData().getDataElements(0, 0,
                                                                     coastDistanceMapWidth,
                                                                     coastDistanceMapHeight,
                                                                     coastDistMapData);
        } catch (Exception e) {
            throw new OperatorException(e);
        } finally {
            if (coastDistProduct != null) {
                coastDistProduct.dispose();
            }
        }

        targetProduct = createRadiometryCorrectedProduct(sourceProduct);
        reflectanceProduct = createReflectanceProduct(targetProduct);
        for (final String bandName : reflectanceProduct.getBandNames()) {
            if (bandName.startsWith("reflec")) {
                ProductUtils.copyBand(bandName, reflectanceProduct, targetProduct, true);
            }
        }
        cloudProduct = createCloudProduct(targetProduct);
        ProductUtils.copyBand("cloud_data_ori_or_flag", cloudProduct, targetProduct, true);
        ProductUtils.copyMasks(cloudProduct, targetProduct);
        addRoiMask();
        mciBand = addMciBand();
        flhBand = addFlhBand();
        targetProduct.addBand("dummy", ProductData.TYPE_UINT8);

        super.initialize();
    }

    @Override
    public void dispose() {
        if (cloudProduct != null) {
            cloudProduct.dispose();
            cloudProduct = null;
        }
        if (reflectanceProduct != null) {
            reflectanceProduct.dispose();
            reflectanceProduct = null;
        }
        super.dispose();
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Product subset = null;

        try {
            final Rectangle rectangle = targetTile.getRectangle();
            subset = createSubset(targetProduct, rectangle);
            final Feature[] features = extractPatchFeatures(rectangle.x / DEFAULT_PATCH_SIZE,
                                                            rectangle.y / DEFAULT_PATCH_SIZE, rectangle, subset);
            // TODO - write features?
        } finally {
            if (subset != null) {
                subset.dispose();
            }
        }
    }


    @Override
    protected Feature[] extractPatchFeatures(int patchX, int patchY, Rectangle subsetRegion, Product patch) {
        if (skipFeaturesOutput && skipQuicklookOutput && skipProductOutput) {
            return null;
        }

        int numPixelsRequired = patchWidth * patchHeight;
        int numPixelsTotal = patch.getSceneRasterWidth() * patch.getSceneRasterHeight();

        double patchPixelRatio = numPixelsTotal / (double) numPixelsRequired;
        if (patchPixelRatio < minValidPixelRatio) {
            getLogger().warning(String.format("Rejected patch x%dy%d, patchPixelRatio=%f%%", patchX, patchY,
                                              patchPixelRatio * 100));
            return null;
        }

        final StxFactory stxFactory = new StxFactory();
        stxFactory.withRoiMask(patch.getMaskGroup().get(FEX_ROI_MASK_NAME));
        final Stx stx = stxFactory.create(mciBand, ProgressMonitor.NULL);

        double validPixelRatio = stx.getSampleCount() / (double) numPixelsRequired;
        if (validPixelRatio < minValidPixelRatio) {
            getLogger().warning(String.format("Rejected patch x%dy%d, validPixelRatio=%f%%", patchX, patchY,
                                              validPixelRatio * 100));
            return null;
        }

        final Mask mask = patch.getMaskGroup().get(FEX_ROI_MASK_NAME);
        final AggregationMetrics aggregationMetrics = AggregationMetrics.compute(mask);
        final ConnectivityMetric connectivityMetric = ConnectivityMetric.compute(mask);
        final RenderedImage[] images = createReflectanceRgbImages(patch, "NOT l1_flags.INVALID", FEX_ROI_MASK_NAME);

        return new Feature[]{
                new Feature(FEATURE_TYPES[0], patch),
                new Feature(FEATURE_TYPES[1], images[0]),
                new Feature(FEATURE_TYPES[2], images[1]),
                createFeature(FEATURE_TYPES[3], patch),
                createFeature(FEATURE_TYPES[4], patch),
                createFeature(FEATURE_TYPES[5], patch),
                new Feature(FEATURE_TYPES[6], validPixelRatio),
                new Feature(FEATURE_TYPES[7], connectivityMetric.connectionRatio),
                new Feature(FEATURE_TYPES[8], connectivityMetric.fractalIndex),
                new Feature(FEATURE_TYPES[9], aggregationMetrics.p11),
                new Feature(FEATURE_TYPES[10],
                            (double) aggregationMetrics.n11 / (double) (aggregationMetrics.n10 + aggregationMetrics.n11)),
                new Feature(FEATURE_TYPES[11], (double) aggregationMetrics.n11 / (double) aggregationMetrics.n10),
                new Feature(FEATURE_TYPES[12], aggregationMetrics.clumpiness),
        };
    }

    private Feature createFeature(FeatureType featureType, Product product) {
        final StxFactory stxFactory = new StxFactory();
        stxFactory.withRoiMask(product.getMaskGroup().get(FEX_ROI_MASK_NAME));
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

        Band r = product.addBand("virtual_red",
                                 "log(0.05 + 0.35 * reflec_2 + 0.60 * reflec_5 + reflec_6 + 0.13 * reflec_7)");
        Band g = product.addBand("virtual_green",
                                 "log(0.05 + 0.21 * reflec_3 + 0.50 * reflec_4 + reflec_5 + 0.38 * reflec_6)");
        Band b = product.addBand("virtual_blue",
                                 "log(0.05 + 0.21 * reflec_1 + 1.75 * reflec_2 + 0.47 * reflec_3 + 0.16 * reflec_4)");

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

    private RenderedImage getRenderedImageDDD(String expressionA, double minR, double maxR, double gammaR, double minG,
                                              double maxG, double gammaG, double minB, double maxB, double gammaB,
                                              Band r, Band g, Band b) {
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
        return algalBloomFex.toArray(new String[algalBloomFex.size()]);
    }

    private static Product createRadiometryCorrectedProduct(Product sourceProduct) {
        final HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("doCalibration", false);
        parameters.put("doSmile", true);
        parameters.put("doEqualization", true);
        parameters.put("reproVersion", ReprocessingVersion.REPROCESSING_3);
        parameters.put("doRadToRefl", false);
        return GPF.createProduct("Meris.CorrectRadiometry", parameters, sourceProduct);
    }

    private static Product createReflectanceProduct(Product sourceProduct) {
        final HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("doCalibration", false);
        parameters.put("doSmile", false);
        parameters.put("doEqualization", false);
        parameters.put("doRadToRefl", true);
        return GPF.createProduct("Meris.CorrectRadiometry", parameters, sourceProduct);
    }

    private static Product createCloudProduct(Product sourceProduct) {
        final MerisCloudMaskOperator op = new MerisCloudMaskOperator();
        op.setSourceProduct(sourceProduct);
        op.setRoiExpr(FEX_VALID_MASK);
        op.setThreshold(9);
        return op.getTargetProduct();
    }

    private void addRoiMask() {
        final String roiExpr = String.format("(%s) AND NOT (%s)", FEX_VALID_MASK, "cloud_mask");
        targetProduct.addMask(FEX_ROI_MASK_NAME, roiExpr, "ROI for pixels used for the feature extraction",
                              Color.green, 0.5);
    }

    private Band addMciBand() {
        final Band l1 = targetProduct.getBand("radiance_8");
        final Band l2 = targetProduct.getBand("radiance_9");
        final Band l3 = targetProduct.getBand("radiance_10");

        final double lambda1 = l1.getSpectralWavelength();
        final double lambda2 = l2.getSpectralWavelength();
        final double lambda3 = l3.getSpectralWavelength();
        final double factor = (lambda2 - lambda1) / (lambda3 - lambda1);
        final double cloudCorrectionFactor = 1.005;

        final Band mci = targetProduct.addBand("mci",
                                               String.format("%s - %s * (%s - (%s - %s) * %s)",
                                                             l2.getName(),
                                                             cloudCorrectionFactor,
                                                             l1.getName(),
                                                             l3.getName(),
                                                             l1.getName(), factor));
        mci.setValidPixelExpression(FEX_ROI_MASK_NAME);
        return mci;
    }

    private Band addFlhBand() {
        final Band l1 = targetProduct.getBand("reflec_7");
        final Band l2 = targetProduct.getBand("reflec_8");
        final Band l3 = targetProduct.getBand("reflec_9");

        final double lambda1 = l1.getSpectralWavelength();
        final double lambda2 = l2.getSpectralWavelength();
        final double lambda3 = l3.getSpectralWavelength();
        final double factor = (lambda2 - lambda1) / (lambda3 - lambda1);
        final double cloudCorrectionFactor = 1.005;

        final Band flh = targetProduct.addBand("flh",
                                               String.format("%s - %s * (%s - (%s - %s) * %s)",
                                                             l2.getName(),
                                                             cloudCorrectionFactor,
                                                             l1.getName(),
                                                             l3.getName(),
                                                             l1.getName(), factor));
        flh.setValidPixelExpression(FEX_ROI_MASK_NAME);
        return flh;
    }

    private void addCoastDistanceBand() {
        final Band coastDistanceBand = targetProduct.addBand("coast_dist", ProductData.TYPE_FLOAT32);
        final DefaultMultiLevelImage coastDistImage = new DefaultMultiLevelImage(
                new AbstractMultiLevelSource(ImageManager.getMultiLevelModel(coastDistanceBand)) {
                    @Override
                    protected RenderedImage createImage(int level) {
                        return new WorldDataOpImage(targetProduct.getGeoCoding(), coastDistanceBand,
                                                    ResolutionLevel.create(getModel(), level),
                                                    coastDistanceMapWidth, coastDistanceMapHeight, coastDistMapData);
                    }
                });
        coastDistanceBand.setSourceImage(coastDistImage);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AlgalBloomFexOperator.class);
        }
    }

}
