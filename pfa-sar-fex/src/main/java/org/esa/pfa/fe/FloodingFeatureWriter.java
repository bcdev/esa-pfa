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
package org.esa.pfa.fe;

import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.fe.op.out.PatchSink;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;

import java.awt.*;
import java.io.IOException;

/**
 * Output features into patches
 */
@OperatorMetadata(alias = "FloodingFeatureWriter",
        authors = "Jun Lu, Luis Veci",
        copyright = "Copyright (C) 2014 by Array Systems Computing Inc.",
        description = "Writes features into patches.",
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
    protected boolean processPatch(Patch patch, PatchSink patchOutput) throws IOException {
        if (skipFeaturesOutput && skipQuicklookOutput && skipProductOutput) {
            return false;
        }

        final int numPixelsRequired = patchWidth * patchHeight;
        if(isPatchTooSmall(patch, numPixelsRequired)) {
            return false;
        }

        final Product featureProduct = patch.getPatchProduct();
        final Band targetBand = getFeatureMask(featureProduct, featureBandName);

        final int tw = targetBand.getRasterWidth();
        final int th = targetBand.getRasterHeight();
        final double patchSize = tw*th;

        final Tile srcTile = getSourceTile(targetBand, new Rectangle(0, 0, tw, th));
        final double[] dataArray = new double[tw*th];

        final RegionGrower blob = new RegionGrower(srcTile);
        blob.run(1, dataArray);
        final double maxClusterSize = blob.getMaxClusterSize();
        final int numSamplesOverThreshold = blob.getNumSamples();

        final double pctOverPnt4 = numSamplesOverThreshold/patchSize;

        //if(pctOverPnt4 < minValidPixels)
        //    return false;

        final Feature[] features = {
                new Feature(featureTypes[0], featureProduct),
                new Feature(featureTypes[1], createColoredBandImage(featureProduct.getBandAt(0), 0, 1)),
                new Feature(featureTypes[2], createColoredBandImage(featureProduct.getBandAt(1), 0, 1)),
                createStxFeature(featureTypes[3], targetBand),
                new Feature(featureTypes[4], pctOverPnt4),
                new Feature(featureTypes[5], maxClusterSize/patchSize),
        };

        patchOutput.writePatch(patch, features);

        disposeProducts(featureProduct);

        return true;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(FloodingFeatureWriter.class);
        }
    }
}