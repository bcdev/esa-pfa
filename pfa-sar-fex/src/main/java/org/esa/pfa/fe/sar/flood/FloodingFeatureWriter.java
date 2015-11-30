/*
 * Copyright (C) 2013 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.pfa.fe.sar.flood;

import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.fe.op.out.PatchSink;
import org.esa.pfa.fe.sar.AbstractSARFeatureWriter;
import org.esa.pfa.fe.sar.RegionGrower;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Output features into patches
 */
@OperatorMetadata(alias = "FloodingFeatureWriter",
        authors = "Jun Lu, Luis Veci",
        copyright = "Copyright (C) 2015 by Array Systems Computing Inc.",
        description = "Writes flood features into patches.",
        category = "Raster/Image Analysis/Feature Extraction")
public class FloodingFeatureWriter extends AbstractSARFeatureWriter {

    public static final String featureBandName = "_flood";

    private FeatureType[] featureTypes;

    public FloodingFeatureWriter() {
        setRequiresAllBands(true);
    }

    @Override
    protected FeatureType[] getFeatureTypes() {
        if (featureTypes == null) {
            featureTypes = new FloodingApplicationDescriptor().getFeatureTypes();
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
        if(mstBand == null) {
            throw new OperatorException("Master band not found");
        }
        final Band slvBand = getFeatureBand(featureProduct, "slv");
        if(slvBand == null) {
            throw new OperatorException("Slave band not found");
        }
        final Band featureMask = getFeatureMask(featureProduct, featureBandName);

        final Band homogeneity = getFeatureBand(featureProduct, "Homogeneity");
        if(homogeneity != null) {
            homogeneity.setValidPixelExpression(featureMask.getName());
        }

        final Band energy = getFeatureBand(featureProduct, "Energy");
        if(energy != null) {
            energy.setValidPixelExpression(featureMask.getName());
        }

        final int tw = featureMask.getRasterWidth();
        final int th = featureMask.getRasterHeight();
        final double patchSize = tw*th;

        final Tile srcTile = getSourceTile(featureMask, new Rectangle(0, 0, tw, th));
        final double[] dataArray = new double[tw*th];

        final RegionGrower blob = new RegionGrower(srcTile);
        blob.run(1, dataArray);
        final double maxClusterSize = blob.getMaxClusterSize();
        final int numSamplesOverThreshold = blob.getNumSamples();

        final double pctOverThreshold = numSamplesOverThreshold/patchSize;

        if(pctOverThreshold < minValidPixels)
            return false;

        final java.util.List<Feature> features = new ArrayList<>();
        if(!skipProductOutput) {
            features.add(new Feature(featureTypes[0], featureProduct));
        }
        if(!skipQuicklookOutput) {
            features.add(new Feature(featureTypes[1], createRgbImage(new Band[]{slvBand, mstBand, featureMask})));
            features.add(new Feature(featureTypes[2], createColoredBandImage(featureMask, 0, 1)));
            features.add(new Feature(featureTypes[3], createColoredBandImage(mstBand, 0, 1)));
            features.add(new Feature(featureTypes[4], createColoredBandImage(slvBand, 0, 1)));
        }
        if(!skipFeaturesOutput) {
            features.add(createStxFeature(featureTypes[5], mstBand));
            features.add(createStxFeature(featureTypes[6], slvBand));
            if(homogeneity != null) {
                features.add(createStxFeature(featureTypes[7], homogeneity));
            }
            if(energy != null) {
                features.add(createStxFeature(featureTypes[8], energy));
            }
            features.add(new Feature(featureTypes[9], pctOverThreshold));
            features.add(new Feature(featureTypes[10], maxClusterSize / patchSize));
        }

        Feature[] featuresArray = features.toArray(new Feature[features.size()]);
        patch.setFeatures(featuresArray);
        sink.writePatch(patch, featuresArray);

        disposeProducts(featureProduct);

        return true;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(FloodingFeatureWriter.class);
        }
    }
}