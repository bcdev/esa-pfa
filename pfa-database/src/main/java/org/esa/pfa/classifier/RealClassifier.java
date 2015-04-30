/*
 * Copyright (C) 2015 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.pfa.classifier;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;

import java.io.File;
import java.io.IOException;

/**
 * The part of the classifier that does the "real" work.
 */
public interface RealClassifier {

    int getNumTrainingImages();

    void setNumTrainingImages(int numTrainingImages);

    int getNumRetrievedImages();

    void setNumRetrievedImages(int numRetrievedImages);

    void saveClassifier() throws IOException;

    void startTraining(Patch[] queryPatches, ProgressMonitor pm) throws IOException;

    Patch[] getMostAmbigousPatches(ProgressMonitor pm);

    void train(Patch[] labeledPatches, ProgressMonitor pm) throws IOException;

    Patch[] classify();

    int getNumIterations();

    FeatureType[] getEffectiveFeatureTypes();

    String[] getAvailableQuickLooks(Patch patch) throws IOException;

    void populateArchivePatches(ProgressMonitor pm);

    void getPatchQuicklook(Patch patch, String quicklookBandName);

    File getPatchProductFile(Patch patch) throws IOException;
}