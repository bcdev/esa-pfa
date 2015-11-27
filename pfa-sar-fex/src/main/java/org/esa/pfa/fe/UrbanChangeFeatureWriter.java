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

/**
 * Output features into patches
 */
@OperatorMetadata(alias = "UrbanChangeFeatureWriter",
        authors = "Jun Lu, Luis Veci",
        copyright = "Copyright (C) 2015 by Array Systems Computing Inc.",
        description = "Writes urban change features into patches.",
        category = "Raster/Image Analysis/Feature Extraction")
public class UrbanChangeFeatureWriter extends AbstractSARFeatureWriter {

    private FeatureType[] featureTypes;

    private final static String featureName = "speckle_divergence";
    private final static double EPS = 1e-15;

    public UrbanChangeFeatureWriter() {
        setRequiresAllBands(true);
    }

    @Override
    protected FeatureType[] getFeatureTypes() {
        if (featureTypes == null) {
            featureTypes = new UrbanChangeApplicationDescriptor().getFeatureTypes();
        }
        return featureTypes;
    }

    @Override
    protected boolean processPatch(Patch patch, PatchSink sink) throws IOException {
        if (skipFeaturesOutput && skipQuicklookOutput && skipProductOutput) {
            return false;
        }

        final int numPixelsRequired = patchWidth * patchHeight;
        if(isPatchTooSmall(patch, numPixelsRequired)) {
            return false;
        }

        final Product featureProduct = patch.getPatchProduct();
        final Band[] mstBands = getFeatureBands(featureProduct, "mst");
        if(mstBands.length == 0) {
            throw new OperatorException("Master bands not found");
        }
        final Band[] slvBands = getFeatureBands(featureProduct, "slv");
        if(slvBands.length == 0) {
            throw new OperatorException("Slave bands not found");
        }

        final Band mstSpkDiv = findBand(mstBands, featureName);
        final Band slvSpkDiv = findBand(slvBands, featureName);
        final String mstName = mstSpkDiv.getName().substring(0, mstSpkDiv.getName().indexOf(featureName)-1);
        final Band mstBand = featureProduct.getBand(mstName);
        final String slvName = slvSpkDiv.getName().substring(0, slvSpkDiv.getName().indexOf(featureName)-1);
        final Band slvBand = featureProduct.getBand(slvName);

        final String expression = "log(max("+mstSpkDiv.getName()+"/"+slvSpkDiv.getName()+", "+EPS+"))";
        final VirtualBand urbanChangeBand = new VirtualBand("urbanChange",
                                                     ProductData.TYPE_FLOAT32,
                                                     mstBand.getRasterWidth(),
                                                     mstBand.getRasterHeight(),
                                                     expression);
        final String validExpression =
                        "(fneq("+mstSpkDiv.getName()+","+mstSpkDiv.getNoDataValue()+")) && "+
                        "(fneq("+slvSpkDiv.getName()+","+slvSpkDiv.getNoDataValue()+")) && "+
                        "(fneq("+mstBand.getName()+","+mstBand.getNoDataValue()+")) && "+
                        "(fneq("+slvBand.getName()+","+slvBand.getNoDataValue()+")) && "+
                        "("+mstSpkDiv.getName()+" > 0.5 || "+slvSpkDiv.getName()+" > 0.5)";
        urbanChangeBand.setValidPixelExpression(validExpression);
        urbanChangeBand.setNoDataValueUsed(true);
        urbanChangeBand.setOwner(featureProduct);
        featureProduct.addBand(urbanChangeBand);

        final Stx stx = urbanChangeBand.getStx();
        final double stdDev = stx.getStandardDeviation();
        if(Double.isNaN(stdDev) || stdDev < 0.2)
            return false;

        final String vband = urbanChangeBand.getName();
        final String maskExpression = vband +" > 2 ? "+vband+" : "+vband+" < -2 ? "+vband+" : NaN";
        final VirtualBand maskBand = new VirtualBand("urbanChangeMask",
                                                            ProductData.TYPE_FLOAT32,
                                                            mstBand.getRasterWidth(),
                                                            mstBand.getRasterHeight(),
                                                            maskExpression);
        maskBand.setValidPixelExpression(validExpression);
        maskBand.setNoDataValueUsed(true);
        maskBand.setOwner(featureProduct);
        featureProduct.addBand(maskBand);

        final java.util.List<Feature> features = new ArrayList<>();
        if(!skipProductOutput) {
            features.add(new Feature(featureTypes[0], featureProduct));
        }
        if(!skipQuicklookOutput) {
            features.add(new Feature(featureTypes[1], createColoredBandImage(urbanChangeBand,
                                                                             urbanChangeBand.getStx().getMinimum(),
                                                                             urbanChangeBand.getStx().getMaximum())));
            features.add(new Feature(featureTypes[2], createColoredBandImage(maskBand,
                                                                             maskBand.getStx().getMinimum(),
                                                                             maskBand.getStx().getMaximum())));
            features.add(new Feature(featureTypes[3], createColoredBandImage(mstBand,
                                                                             0,
                                                                             1)));
            features.add(new Feature(featureTypes[4], createColoredBandImage(slvBand,
                                                                             0,
                                                                             1)));
            features.add(new Feature(featureTypes[5], createColoredBandImage(mstSpkDiv,
                                                                             mstSpkDiv.getStx().getMinimum(),
                                                                             mstSpkDiv.getStx().getMaximum())));
            features.add(new Feature(featureTypes[6], createColoredBandImage(slvSpkDiv,
                                                                             slvSpkDiv.getStx().getMinimum(),
                                                                             slvSpkDiv.getStx().getMaximum())));
        }
        if(!skipFeaturesOutput) {
            features.add(createStxFeature(featureTypes[7], mstSpkDiv));
            features.add(createStxFeature(featureTypes[8], slvSpkDiv));
            features.add(createStxFeature(featureTypes[9], urbanChangeBand));
            features.add(createStxFeature(featureTypes[10], maskBand));
        }

        Feature[] featuresArray = features.toArray(new Feature[features.size()]);
        patch.setFeatures(featuresArray);
        sink.writePatch(patch, featuresArray);

        disposeProducts(featureProduct);

        return true;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(UrbanChangeFeatureWriter.class);
        }
    }
}