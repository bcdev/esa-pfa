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


    public static final String FEX_VALID_MASK = "NOT (l1_flags.INVALID OR l1_flags.LAND_OCEAN OR l1_flags.BRIGHT OR l1_flags.GLINT_RISK)";
    public static final String FEX_CLOUD_MASK = "distance(radiance_2/radiance_1,radiance_3/radiance_1,radiance_4/radiance_1,radiance_5/radiance_1,radiance_6/radiance_1,radiance_7/radiance_1,radiance_8/radiance_1,radiance_9/radiance_1,radiance_10/radiance_1,radiance_11/radiance_1,radiance_12/radiance_1,radiance_13/radiance_1,radiance_14/radiance_1,radiance_15/radiance_1," +
            "1.0744720212301275,1.0733119255986385,1.0479161563791768,0.9315456603467167,0.8480901646693009,0.8288787653690769,0.8071113370747969,0.764724290638019,0.7128622108550259,0.23310952131660026,0.666867538685338,0.5517312317788085,0.5319259202911271,0.4004727059350037)/15 < 0.095";
    public static final String FEX_VALID_MASK_NAME = "fex_valid";
    public static final String FEX_CLOUD_MASK_NAME = "fex_cloud";
    public static final String FEX_COAST_DIST_PRODUCT_PATH = "auxdata/coast_dist_2880.dim";

    @Parameter(defaultValue = "0.2")
    private double minValidPixelRatio;

    private transient float[] coastDistData;
    private transient int coastDistWidth;
    private transient int coastDistHeight;

    public static final FeatureType[] FEATURE_TYPES = new FeatureType[]{
            new FeatureType("patch", "Patch product", Product.class),
            new FeatureType("patch_ql", "Quicklook for patch product", RenderedImage.class),
            new FeatureType("patch_ql_masked", "Masked quicklook for patch product", RenderedImage.class),
            new FeatureType("mci", "Maximum Chlorophyll Index", STX_ATTRIBUTE_TYPES),
            new FeatureType("flh", "Fluorescence Line Height", STX_ATTRIBUTE_TYPES),
            new FeatureType("coast_dist", "Distance from next coast pixel (km)", STX_ATTRIBUTE_TYPES),
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

        final Product correctedProduct = createCorrectedProduct(patchProduct);
        addCloudMask(correctedProduct);
        addValidMask(correctedProduct);
        final Band mciBand = addMciBand(correctedProduct);

        final StxFactory stxFactory = new StxFactory();
        stxFactory.withRoiMask(correctedProduct.getMaskGroup().get(FEX_VALID_MASK_NAME));
        final Stx stx = stxFactory.create(mciBand, ProgressMonitor.NULL);

        double validPixelRatio = stx.getSampleCount() / (double) numPixelsRequired;
        if (validPixelRatio < minValidPixelRatio) {
            getLogger().warning(String.format("Rejected patch x%dy%d, validPixelRatio=%f%%", patchX, patchY, validPixelRatio * 100));
            return null;
        }

        final Product reflectanceProduct = createReflectanceProduct(correctedProduct);
        for (final String bandName : reflectanceProduct.getBandNames()) {
            if (bandName.startsWith("reflec")) {
                ProductUtils.copyBand(bandName, reflectanceProduct, correctedProduct, true);
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
                new Feature(FEATURE_TYPES[0], reflectanceProduct),
                new Feature(FEATURE_TYPES[1], createReflectanceRgbImage(reflectanceProduct)),
                new Feature(FEATURE_TYPES[2], createReflectanceRgbImageMasked(reflectanceProduct)),
                createFeature(FEATURE_TYPES[3], correctedProduct),
                createFeature(FEATURE_TYPES[4], correctedProduct),
                createFeature(FEATURE_TYPES[5], correctedProduct),
        };

        coastDistImage.dispose();
        reflectanceProduct.dispose();
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
        return new Feature(featureType, null,
                           stx.getMinimum(),
                           stx.getMaximum(),
                           stx.getMean(),
                           stx.getMedian(),
                           stx.getStandardDeviation(),
                           stx.getSampleCount());
    }

    private RenderedImage createReflectanceRgbImageMasked(Product product) {
        return createReflectanceRgbImage(product, FEX_VALID_MASK);
    }

    private RenderedImage createReflectanceRgbImage(Product product) {
        return createReflectanceRgbImage(product, "NOT l1_flags.INVALID");
    }

    private RenderedImage createReflectanceRgbImage(Product product, String validMask) {
        return createImage(product,
                           "log(0.05 + 0.35 * reflec_2 + 0.60 * reflec_5 + reflec_6 + 0.13 * reflec_7)",
                           "log(0.05 + 0.21 * reflec_3 + 0.50 * reflec_4 + reflec_5 + 0.38 * reflec_6)",
                           "log(0.05 + 0.21 * reflec_1 + 1.75 * reflec_2 + 0.47 * reflec_3 + 0.16 * reflec_4)",
                           validMask,
                           -2.0, -1.0, 1.2,
                           -2.0, -1.0, 1.2,
                           -1.5, -0.5, 1.5);
                           /*
                           -2, 0, 1.0,
                           -2, 0, 1.0,
                           -2, 0, 1.0);
                           */
                           /*
                           -1.95, -1.35, 1.1,
                           -1.9, -1.4, 1.1,
                           -1.3, -0.7, 1.0);
                           */
    }

    private RenderedImage createImage(Product product,
                                      String expressionR,
                                      String expressionG,
                                      String expressionB,
                                      String expressionA,
                                      double minR, double maxR, double gammaR,
                                      double minG, double maxG, double gammaG,
                                      double minB, double maxB, double gammaB) {
        Band r = product.addBand("virtual_red", expressionR);
        Band g = product.addBand("virtual_green", expressionG);
        Band b = product.addBand("virtual_blue", expressionB);

        r.setValidPixelExpression(expressionA);
        r.setNoDataValue(Double.NaN);
        r.setNoDataValueUsed(true);

        RGBChannelDef rgbChannelDef = new RGBChannelDef(new String[]{"virtual_red", "virtual_green", "virtual_blue"});
        rgbChannelDef.setMinDisplaySample(0, minR);
        rgbChannelDef.setMaxDisplaySample(0, maxR);
        rgbChannelDef.setGamma(0, gammaR);

        rgbChannelDef.setMinDisplaySample(1, minG);
        rgbChannelDef.setMaxDisplaySample(1, maxG);
        rgbChannelDef.setGamma(1, gammaG);

        rgbChannelDef.setMinDisplaySample(2, minB);
        rgbChannelDef.setMaxDisplaySample(2, maxB);
        rgbChannelDef.setGamma(2, gammaB);

        RenderedImage rgbaImage = ImageManager.getInstance().createColoredBandImage(new Band[]{r, g, b},
                                                                                    new ImageInfo(rgbChannelDef), 0);
        product.removeBand(r);
        product.removeBand(g);
        product.removeBand(b);

        return rgbaImage;
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
