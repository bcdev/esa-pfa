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
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.Stx;
import org.esa.beam.framework.datamodel.StxFactory;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.main.GPT;
import org.esa.beam.jai.ImageManager;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.meris.radiometry.equalization.ReprocessingVersion;
import org.esa.beam.util.ProductUtils;
import org.esa.rss.pfa.fe.op.AttributeType;
import org.esa.rss.pfa.fe.op.Feature;
import org.esa.rss.pfa.fe.op.FeatureType;
import org.esa.rss.pfa.fe.op.FexOperator;

import javax.media.jai.JAI;
import java.awt.Color;
import java.awt.image.DataBufferFloat;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.HashMap;

/**
 * An operator for extracting algal bloom features.
 *
 * @author Norman Fomferra
 * @author Ralf Quast
 */
public class AlgalBloomFexOperator extends FexOperator {

    private static final long KiB = 1024L;
    private static final long MiB = 1024L * KiB;
    private static final long GiB = 1024L * MiB;

    private static final int TILE_SIZE_X = 200;
    private static final int TILE_SIZE_Y = 200;

    public static final String FEX_EXTENSION = ".fex";
    public static final String FEX_VALID_MASK = "NOT (l1_flags.INVALID OR l1_flags.LAND_OCEAN OR l1_flags.BRIGHT OR l1_flags.GLINT_RISK)";
    public static final String FEX_CLOUD_MASK = "distance(radiance_2/radiance_1,radiance_3/radiance_1,radiance_4/radiance_1,radiance_5/radiance_1,radiance_6/radiance_1,radiance_7/radiance_1,radiance_8/radiance_1,radiance_9/radiance_1,radiance_10/radiance_1,radiance_11/radiance_1,radiance_12/radiance_1,radiance_13/radiance_1,radiance_14/radiance_1,radiance_15/radiance_1," +
            "1.0744720212301275,1.0733119255986385,1.0479161563791768,0.9315456603467167,0.8480901646693009,0.8288787653690769,0.8071113370747969,0.764724290638019,0.7128622108550259,0.23310952131660026,0.666867538685338,0.5517312317788085,0.5319259202911271,0.4004727059350037)/15 < 0.095";
    public static final String FEX_VALID_MASK_NAME = "fex_valid";
    private static final String FEX_CLOUD_MASK_NAME = "fex_cloud";

    public static final String FEX_COAST_DIST_PRODUCT_PATH = "auxdata/coast_dist_2880.dim";
    private static final String FEX_VERSION = "1.1";

    private static boolean skipFeaturesOutput = Boolean.getBoolean("skipFeatures");
    private static boolean skipRgbImageOutput = Boolean.getBoolean("skipRgbImage");
    private static boolean skipProductOutput = Boolean.getBoolean("skipProduct");
    private static boolean equirectangular = Boolean.getBoolean("equirectangular");

    @Parameter(defaultValue = "0.25")
    private double validPixelRatioThreshold;


    static {
        System.setProperty("beam.reader.tileWidth", String.valueOf(TILE_SIZE_X));
        System.setProperty("beam.reader.tileHeight", String.valueOf(TILE_SIZE_Y));

        JAI.getDefaultInstance().getTileCache().setMemoryCapacity(4 * GiB);
        JAI.getDefaultInstance().getTileScheduler().setParallelism(Runtime.getRuntime().availableProcessors());
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }

    private transient float[] coastDistData;
    private transient int coastDistWidth;
    private transient int coastDistHeight;
    public static final AttributeType[] STX_ATTRIBUTE_TYPES = new AttributeType[]{
            new AttributeType("mean", "Mean value of valid feature pixels", Double.class),
            new AttributeType("median", "Median value of valid feature pixels (estimation from 512-bin histogram)", Double.class),
            new AttributeType("stdev", "Standard deviation of valid feature pixels", Double.class),
            new AttributeType("count", "Number of valid feature pixels (currently the same for all features, since only a single mask is used)", Integer.class),
    };
    public static final FeatureType[] FEATURE_TYPES = new FeatureType[]{
            new FeatureType("mci", "Maximum Chlorophyll Index", null, STX_ATTRIBUTE_TYPES),
            new FeatureType("flh", "Fluorescence Line Height", null, STX_ATTRIBUTE_TYPES),
            new FeatureType("coast_dist", "Distance from coast in km", null, STX_ATTRIBUTE_TYPES),
    };

    @Override
    protected FeatureType[] getFeatureTypes() {
        return FEATURE_TYPES;
    }

    @Override
    public void initialize() throws OperatorException {
        super.initialize();

        Product coastDistProduct = null;
        try {
            coastDistProduct = ProductIO.readProduct(FEX_COAST_DIST_PRODUCT_PATH);
        } catch (IOException e) {
            throw new OperatorException(e);
        }

        //Band coastDistance = coastDistProduct.getBand("coast_dist");
        final Band coastDistance = coastDistProduct.addBand("coast_dist_nm_cleaned",
                                                            "coast_dist_nm > 300.0 ? 300.0 : coast_dist_nm");
        coastDistWidth = coastDistProduct.getSceneRasterWidth();
        coastDistHeight = coastDistProduct.getSceneRasterHeight();
        coastDistData = ((DataBufferFloat) coastDistance.getSourceImage().getData().getDataBuffer()).getData();
        coastDistProduct.dispose();
    }

    @Override
    protected Feature[] extractPatchFeatures(Product patchProduct) {
        if (skipFeaturesOutput && skipRgbImageOutput && skipProductOutput) {
            return null;
        }

        int numPixelsRequired = patchWidth * patchHeight;
        int numPixelsTotal = patchProduct.getSceneRasterWidth() * patchProduct.getSceneRasterHeight();

        double patchPixelRatio = numPixelsTotal / (double) numPixelsRequired;
        if (patchPixelRatio < validPixelRatioThreshold) {
            return null;
        }

        final Product correctedProduct = createCorrectedProduct(patchProduct);
        addCloudMask(correctedProduct);
        addValidMask(correctedProduct);
        final Band mciBand = addMciBand(correctedProduct);

        final StxFactory stxFactory = new StxFactory();
        stxFactory.withRoiMask(correctedProduct.getMaskGroup().get(FEX_VALID_MASK_NAME));
        final Stx stx = stxFactory.create(mciBand, ProgressMonitor.NULL);

        double validPixelRatio = stx.getSampleCount() / (double) numPixelsRequired;
        if (validPixelRatio < validPixelRatioThreshold) {
            return null;
        }

        final Product waterProduct = createReflectanceProduct(correctedProduct);
        for (final String bandName : waterProduct.getBandNames()) {
            if (bandName.startsWith("reflec")) {
                ProductUtils.copyBand(bandName, waterProduct, correctedProduct, true);
            }
        }
        addFlhBand(correctedProduct);

        // todo - dirty code from NF, clean up
        final Band coastDistBand = correctedProduct.addBand("coast_dist", ProductData.TYPE_FLOAT32);
        final DefaultMultiLevelImage coastDistImage = new DefaultMultiLevelImage(
                new AbstractMultiLevelSource(ImageManager.getMultiLevelModel(coastDistBand)) {
                    @Override
                    protected RenderedImage createImage(int level) {
                        return new WorldDataOpImage(correctedProduct.getGeoCoding(), coastDistBand,
                                                    ResolutionLevel.create(getModel(), level),
                                                    coastDistWidth, coastDistHeight, coastDistData);
                    }
                });
        coastDistBand.setSourceImage(coastDistImage);

        Feature[] features = {
                createFeature(FEATURE_TYPES[0], correctedProduct),
                createFeature(FEATURE_TYPES[1], correctedProduct),
                createFeature(FEATURE_TYPES[2], correctedProduct),
        };

        coastDistImage.dispose();
        waterProduct.dispose();
        correctedProduct.dispose();

        return features;
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
        final String expression = String.format("(%s) AND NOT (%s)", FEX_VALID_MASK, FEX_CLOUD_MASK_NAME);
        featureProduct.addMask(FEX_VALID_MASK_NAME, expression, "", Color.green, 0.5);
    }

    private void addCloudMask(Product product) {
        product.addMask(FEX_CLOUD_MASK_NAME, FEX_CLOUD_MASK, "Special MERIS L1B 'cloud' mask for PFA", Color.YELLOW,
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
        return new Feature<Object>(featureType, null, stx.getMean(), stx.getMedian(), stx.getStandardDeviation(), stx.getSampleCount());
    }

    public static void main(String[] args) {
        try {
            GPT.main(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
