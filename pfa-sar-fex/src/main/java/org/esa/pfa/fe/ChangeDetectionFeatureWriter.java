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
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Output features into patches
 */
@OperatorMetadata(alias = "ChangeDetectionFeatureWriter",
        authors = "Jun Lu, Luis Veci",
        copyright = "Copyright (C) 2015 by Array Systems Computing Inc.",
        description = "Writes features into patches.",
        category = "Raster/Image Analysis/Feature Extraction")
public class ChangeDetectionFeatureWriter extends AbstractSARFeatureWriter {

    public static final String featureBandName = "ratio";

    private FeatureType[] featureTypes;

    public ChangeDetectionFeatureWriter() {
        setRequiresAllBands(true);
    }

    @Override
    protected FeatureType[] getFeatureTypes() {
        if (featureTypes == null) {
            featureTypes = new ChangeDetectionApplicationDescriptor().getFeatureTypes();
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
        final Band mstBand = getFeatureBand(featureProduct, "mst");
        final Band slvBand = getFeatureBand(featureProduct, "slv");
        final Band targetBand = getFeatureBand(featureProduct, featureBandName);
        final Band featureMask = getFeatureMask(featureProduct, featureBandName);

        final String expression = mstBand.getName() + " / " + slvBand.getName();
        final VirtualBand virtBand = new VirtualBand("mstSlvRatio",
                                                     ProductData.TYPE_FLOAT32,
                                                     mstBand.getSceneRasterWidth(),
                                                     mstBand.getSceneRasterHeight(),
                                                     expression);
        virtBand.setNoDataValueUsed(true);
        virtBand.setOwner(featureProduct);
        featureProduct.addBand(virtBand);

        final int tw = targetBand.getRasterWidth();
        final int th = targetBand.getRasterHeight();
        final double patchSize = tw*th;

        final Stx stx = targetBand.getStx();
        final double pctValid = stx.getSampleCount()/patchSize;
        if(pctValid < minValidPixels)
            return false;

        final Tile srcTile = getSourceTile(targetBand, new Rectangle(0, 0, tw, th));
        final double[] dataArray = new double[tw*th];

        final RegionGrower blob = new RegionGrower(srcTile);
        blob.run(2, dataArray);
        final double maxClusterSize = blob.getMaxClusterSize();
        final int numSamplesOverThreshold = blob.getNumSamples();

        final double percentOver2 = numSamplesOverThreshold/patchSize;

        //if(percentOver2 < minValidPixels)
        //    return false;

        final java.util.List<Feature> features = new ArrayList<>();
        if(!skipProductOutput) {
            features.add(new Feature(featureTypes[0], featureProduct));
        }
        if(!skipQuicklookOutput) {
            features.add(new Feature(featureTypes[1], createRgbImage(new Band[]{mstBand, slvBand, virtBand})));
            features.add(new Feature(featureTypes[2], createColoredBandImage(targetBand, -5, 5)));
            features.add(new Feature(featureTypes[3], createColoredBandImage(featureMask, -5, 5)));
        }
        if(!skipFeaturesOutput) {
            features.add(createStxFeature(featureTypes[4], targetBand));
            features.add(new Feature(featureTypes[5], percentOver2));
            features.add(new Feature(featureTypes[6], maxClusterSize / patchSize));
        }

        sink.writePatch(patch, features.toArray(new Feature[features.size()]));

        disposeProducts(featureProduct);

        return true;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ChangeDetectionFeatureWriter.class);
        }
    }
}