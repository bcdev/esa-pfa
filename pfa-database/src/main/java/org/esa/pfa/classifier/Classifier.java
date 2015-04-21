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
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.op.Patch;

import java.io.IOException;

/**
 * The classifier
 */
public class Classifier {

    private final String classifierName;
    private final PFAApplicationDescriptor applicationDescriptor;
    private final RealClassifier realClassifier;

    public Classifier(String classifierName, PFAApplicationDescriptor applicationDescriptor, RealClassifier realClassifier) {
        this.classifierName = classifierName;
        this.applicationDescriptor = applicationDescriptor;
        this.realClassifier = realClassifier;
    }

    public String getName() {
        return classifierName;
    }

    public PFAApplicationDescriptor getApplicationDescriptor() {
        return applicationDescriptor;
    }

    // org.esa.pfa.search.Classifier.setQueryImages()
    void startTraining(Patch[] queryPatches, ProgressMonitor pm) throws IOException {
        realClassifier.startTraining(queryPatches, pm);
    }

    // org.esa.pfa.search.Classifier.getImagesToLabel()
    public Patch[] getMostAmbigousPatches(ProgressMonitor pm) {
        return realClassifier.getMostAmbigousPatches(pm);
    }

    // org.esa.pfa.search.Classifier.trainModel()
    public void train(Patch[] labeledPatches, ProgressMonitor pm) throws IOException {
        realClassifier.train(labeledPatches, pm);
    }

    // org.esa.pfa.search.Classifier.getRetrievedImages()
    public Patch[] classify() {
        return realClassifier.classify();
    }



}
