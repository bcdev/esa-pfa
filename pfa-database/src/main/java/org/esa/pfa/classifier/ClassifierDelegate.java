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
import org.esa.pfa.fe.op.Patch;

import java.io.File;
import java.io.IOException;

/**
 * The classifier
 */
public class ClassifierDelegate {

    private final String classifierName;
    private final Classifier classifier;

    public ClassifierDelegate(String classifierName, Classifier classifier) {
        this.classifierName = classifierName;
        this.classifier = classifier;
    }

    public String getName() {
        return classifierName;
    }

    public int getNumTrainingImages() {
        return classifier.getNumTrainingImages();
    }

    public void setNumTrainingImages(int numTrainingImages) {
        classifier.setNumTrainingImages(numTrainingImages);
    }

    public int getNumRetrievedImages() {
        return classifier.getNumRetrievedImages();
    }

    public void setNumRetrievedImages(int numRetrievedImages) {
        classifier.setNumRetrievedImages(numRetrievedImages);
    }

    public int getNumIterations() {
        return classifier.getNumIterations();
    }

    public Patch[] startTraining(Patch[] queryPatches, ProgressMonitor pm) throws IOException {
        return classifier.startTraining(queryPatches, pm);
    }

    public Patch[] trainAndClassify(boolean prePopulate, Patch[] labeledPatches, ProgressMonitor pm) throws IOException {
        return classifier.trainAndClassify(prePopulate, labeledPatches, pm);
    }

    public Patch[] getMostAmbigous(boolean prePopulate, ProgressMonitor pm) throws IOException {
        return classifier.getMostAmbigous(prePopulate, pm);
    }

    public void getPatchQuicklook(Patch patch, String quicklookBandName) {
        classifier.getPatchQuicklook(patch, quicklookBandName);
    }

    public File getPatchProductFile(Patch patch) throws IOException {
        return classifier.getPatchProductFile(patch);
    }
}
