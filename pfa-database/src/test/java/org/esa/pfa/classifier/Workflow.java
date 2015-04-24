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

import java.io.IOException;

/**
 * Created by marcoz on 20.04.15.
 */
public class Workflow {

    public static void main(String[] args) throws IOException {

        ClassifierManager classifierManager = new LocalClassifierManager("responsibleURL");

        /////////////////////////////////////////////////////////

        Classifier classifier;
        classifier = classifierManager.create("classifierName", "appName");

        classifier = classifierManager.get("classifierName");

        classifierManager.delete("classifierName");

        String[] availableClassifier = classifierManager.list();

        /////////////////////////////////////////////////////////

        String classifierName = classifier.getName();

        Patch[] queryPatches = null;
        classifier.startTraining(queryPatches, ProgressMonitor.NULL);

        Patch[] mostAmbigousPatches = classifier.getMostAmbigousPatches(ProgressMonitor.NULL);
        Patch[] labeledPatches = mostAmbigousPatches;
        classifier.train(labeledPatches, ProgressMonitor.NULL);

        Patch[] bestPatches = classifier.classify();
    }
}
