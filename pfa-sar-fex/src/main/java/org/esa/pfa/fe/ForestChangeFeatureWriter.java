/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.pfa.fe;

import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.fe.op.out.PatchSink;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.Stx;
import org.esa.snap.core.datamodel.VirtualBand;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;

/**
 * Output features into patches
 */
@OperatorMetadata(alias = "ForestChangeFeatureWriter",
        authors = "Jun Lu, Luis Veci",
        copyright = "Copyright (C) 2015 by Array Systems Computing Inc.",
        description = "Writes forest features into patches.",
        category = "Raster/Image Analysis/Feature Extraction")
public class ForestChangeFeatureWriter extends AbstractSARFeatureWriter {

    private FeatureType[] featureTypes;

    private final static String landcoverName = "MODIS 2010 Tree Cover Percentage";
    private final static double EPS = 1e-15;

    private final static String featureContrast = "Contrast";
    private final static String featureHomogeneity = "Homogeneity";
    private final static String featureEnergy = "Energy";
    private final static String featureEntropy = "Entropy";
    private final static String featureGLCMVariance = "GLCMVariance";
    private final static String[] featureNames = new String[]{
            featureContrast,
            featureHomogeneity,
            featureEnergy,
            featureEntropy,
            featureGLCMVariance
    };

    public ForestChangeFeatureWriter() {
        setRequiresAllBands(true);
    }

    @Override
    protected FeatureType[] getFeatureTypes() {
        if (featureTypes == null) {
            featureTypes = new ForestChangeApplicationDescriptor().getFeatureTypes();
        }
        return featureTypes;
    }

    @Override
    protected boolean processPatch(Patch patch, PatchSink sink) throws IOException {
        if (skipFeaturesOutput && skipQuicklookOutput && skipProductOutput) {
            return false;
        }

        final int numPixelsRequired = patchWidth * patchHeight;
        if (isPatchTooSmall(patch, numPixelsRequired)) {
            return false;
        }

        final Product featureProduct = patch.getPatchProduct();
        final Band[] mstBands = getFeatureBands(featureProduct, "mst");
        if (mstBands.length == 0) {
            throw new OperatorException("Master bands not found");
        }
        final Band[] slvBands = getFeatureBands(featureProduct, "slv");
        if (slvBands.length == 0) {
            throw new OperatorException("Slave bands not found");
        }

        final Band[] mstTextureFeatures = getTextureFeatureBands(mstBands);
        final Band[] slvTextureFeatures = getTextureFeatureBands(slvBands);

        final String mstName = mstTextureFeatures[0].getName().substring(0, mstTextureFeatures[0].getName().indexOf(featureContrast) - 1);
        final Band mstBand = featureProduct.getBand(mstName);
        final String slvName = slvTextureFeatures[0].getName().substring(0, slvTextureFeatures[0].getName().indexOf(featureContrast) - 1);
        final Band slvBand = featureProduct.getBand(slvName);

        final Band landcover = getFeatureBand(featureProduct, landcoverName);
        if (landcover == null) {
            throw new OperatorException(landcoverName + " band not found");
        }

        final Band[] changeBands = getChangeBands(featureProduct, mstTextureFeatures, slvTextureFeatures, landcover);

        final Stx stx = changeBands[0].getStx();
        final double mean = stx.getMean();
        if (Double.isNaN(mean) || stx.getSampleCount() < numPixelsRequired/4) //|| (mean < 0.5 && mean > -0.5))
            return false;


        final Band mstMasked = addMaskedBand(featureProduct, mstBand, landcover);
        final Band slvMasked = addMaskedBand(featureProduct, slvBand, landcover);

        final java.util.List<Feature> features = new ArrayList<>();
        if (!skipProductOutput) {
            features.add(new Feature(featureTypes[0], featureProduct));
        }
        if (!skipQuicklookOutput) {
            features.add(new Feature(featureTypes[1], createRgbImage(new Band[]{mstMasked, slvMasked, changeBands[0]})));
            features.add(new Feature(featureTypes[2], createColoredBandImage(changeBands[0],
                                                                             changeBands[0].getStx().getMinimum(),
                                                                             changeBands[0].getStx().getMaximum())));
            features.add(new Feature(featureTypes[3], createColoredBandImage(mstBand, 0, 1)));
            features.add(new Feature(featureTypes[4], createColoredBandImage(slvBand, 0, 1)));
        }
        if (!skipFeaturesOutput) {
            features.add(new Feature(featureTypes[5], mstBand.getProduct().getStartTime().getAsCalendar().get(Calendar.MONTH)));
            features.add(new Feature(featureTypes[6], slvBand.getProduct().getStartTime().getAsCalendar().get(Calendar.MONTH)));
            int f = 7;
            for(Band changeBand : changeBands) {
                features.add(createStxFeature(featureTypes[f++], changeBand));
            }
        }

        Feature[] featuresArray = features.toArray(new Feature[features.size()]);
        patch.setFeatures(featuresArray);
        sink.writePatch(patch, featuresArray);

        disposeProducts(featureProduct);

        return true;
    }

    private static Band[] getTextureFeatureBands(final Band[] availableBands) {
        final Band[] bandList = new Band[featureNames.length];
        int i = 0;
        for (String featureName : featureNames) {
            bandList[i] = findBand(availableBands, featureName);
            if (bandList[i] == null) {
                throw new OperatorException(featureName + " band not found");
            }
            ++i;
        }
        return bandList;
    }

    private static Band[] getChangeBands(final Product featureProduct,
                                         final Band[] mstTextureFeatures, final Band[] slvTextureFeatures,
                                         final Band landcover) {
        final Band[] bandList = new Band[featureNames.length];
        for (int i = 0; i < featureNames.length; ++i) {
            bandList[i] = addChangeBand(featureProduct, featureNames[i], mstTextureFeatures[i], slvTextureFeatures[i], landcover);
        }
        return bandList;
    }

    private static Band addChangeBand(final Product featureProduct, final String featureName,
                                      final Band mstBand, final Band slvBand, final Band landcover) {

        final String expression = "log(max( " + mstBand.getName() + " / " + slvBand.getName() + ", " + EPS + " ))";
        //final String expression = mstBand.getName()+ " / " + slvBand.getName();
        final VirtualBand changeBand = new VirtualBand(featureName+"Change",
                                                       ProductData.TYPE_FLOAT32,
                                                       mstBand.getRasterWidth(),
                                                       mstBand.getRasterHeight(),
                                                       expression);
        final String validExpression =
                "(fneq(" + mstBand.getName() + "," + mstBand.getNoDataValue() + ")) && " +
                        "(fneq(" + slvBand.getName() + "," + slvBand.getNoDataValue() + ")) && " +
                        "('" + landcover.getName() + "' > 50 )";
        changeBand.setValidPixelExpression(validExpression);
        changeBand.setNoDataValueUsed(true);
        changeBand.setOwner(featureProduct);
        featureProduct.addBand(changeBand);

        return changeBand;
    }

    private static Band addMaskedBand(final Product featureProduct, final Band band, final Band landcover) {

        final String expression = band.getName();
        final VirtualBand maskedBand = new VirtualBand(band.getName()+"_Masked",
                                                       ProductData.TYPE_FLOAT32,
                                                       band.getRasterWidth(),
                                                       band.getRasterHeight(),
                                                       expression);
        final String validExpression =
                "(fneq(" + band.getName() + "," + band.getNoDataValue() + ")) && " +
                        "('" + landcover.getName() + "' > 50 )";
        maskedBand.setValidPixelExpression(validExpression);
        maskedBand.setNoDataValueUsed(true);
        maskedBand.setOwner(featureProduct);
        featureProduct.addBand(maskedBand);

        return maskedBand;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ForestChangeFeatureWriter.class);
        }
    }
}