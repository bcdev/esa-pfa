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
package org.esa.pfa.fe.sar.urbanarea;

import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.fe.op.out.PatchSink;
import org.esa.pfa.fe.sar.AbstractSARFeatureWriter;
import org.esa.pfa.fe.sar.RegionGrower;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.Stx;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Output features into patches
 */
@OperatorMetadata(alias = "UrbanAreaFeatureWriter",
        authors = "Jun Lu, Luis Veci",
        copyright = "Copyright (C) 2013 by Array Systems Computing Inc.",
        description = "Writes features into patches.",
        category = "Raster/Image Analysis/Feature Extraction")
public class UrbanAreaFeatureWriter extends AbstractSARFeatureWriter {

    public static final String featureBandName = "_speckle_divergence";

    private static final double Tdsl = 0.4; // threshold for detection adopted from Esch's paper.

    private FeatureType[] featureTypes;

    public UrbanAreaFeatureWriter() {
        setRequiresAllBands(true);
    }

    @Override
    protected FeatureType[] getFeatureTypes() {
        if (featureTypes == null) {
            featureTypes = new UrbanAreaApplicationDescriptor().getFeatureTypes();
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
        final Band targetBand = getFeatureBand(featureProduct, featureBandName);
        if(targetBand == null) {
            throw new OperatorException(featureBandName+" band not found");
        }

        final int tw = targetBand.getRasterWidth();
        final int th = targetBand.getRasterHeight();
        final double patchSize = tw*th;

        final Stx stx = targetBand.getStx();
        final double pctValid = (stx.getSampleCount()/patchSize);
        if(pctValid < minValidPixels)
            return false;

        final Tile srcTile = getSourceTile(targetBand, new Rectangle(0, 0, tw, th));
        final double[] dataArray = new double[tw*th];

        final RegionGrower blob = new RegionGrower(srcTile);
        blob.run(Tdsl, dataArray);
        final double maxClusterSize = blob.getMaxClusterSize();
        final int numSamplesOverThreshold = blob.getNumSamples();

        final double pctOverPnt4 = numSamplesOverThreshold/patchSize;

        if(pctOverPnt4 < minValidPixels)
            return false;

        final List<Feature> features = new ArrayList<>();
        if(!skipProductOutput) {
            features.add(new Feature(featureTypes[0], featureProduct));
        }
        if(!skipQuicklookOutput) {
            features.add(new Feature(featureTypes[1], createColoredBandImage(featureProduct.getBandAt(0), 0, 1)));
            features.add(new Feature(featureTypes[2], createColoredBandImage(featureProduct.getBandAt(1), 0, 1)));
        }
        if(!skipFeaturesOutput) {
            features.add(createStxFeature(featureTypes[3], targetBand));
            features.add(new Feature(featureTypes[4], pctOverPnt4));
            features.add(new Feature(featureTypes[5], maxClusterSize / patchSize));
        }

        Feature[] featuresArray = features.toArray(new Feature[features.size()]);
        patch.setFeatures(featuresArray);
        sink.writePatch(patch, featuresArray);

        disposeProducts(featureProduct);

        return true;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(UrbanAreaFeatureWriter.class);
        }
    }
}