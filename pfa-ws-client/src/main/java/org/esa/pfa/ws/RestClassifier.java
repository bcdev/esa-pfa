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

package org.esa.pfa.ws;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.pfa.classifier.Classifier;
import org.esa.pfa.classifier.ClassifierModel;
import org.esa.pfa.fe.op.Patch;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * Created by marcoz on 24.04.15.
 */
public class RestClassifier implements Classifier {


    private final String classifierName;
    private ClassifierModel model;
    private final RestClient restClient;


    public RestClassifier(String classifierName, ClassifierModel model, RestClient restClient) {
        this.classifierName = classifierName;
        this.model = model;
        this.restClient = restClient;
    }

    @Override
    public String getName() {
        return classifierName;
    }

    @Override
    public int getNumTrainingImages() {
        return model.getNumTrainingImages();
    }

    @Override
    public void setNumTrainingImages(int numTrainingImages) {
        model.setNumTrainingImages(numTrainingImages);
    }

    @Override
    public int getNumRetrievedImages() {
        return model.getNumRetrievedImages();
    }

    @Override
    public void setNumRetrievedImages(int numRetrievedImages) {
        model.setNumRetrievedImages(numRetrievedImages);
    }

    @Override
    public Patch[] startTraining(Patch[] queryPatches, ProgressMonitor pm) throws IOException {
        return restClient.startTraining(classifierName, queryPatches);
    }

    @Override
    public Patch[] trainAndClassify(boolean prePopulate, Patch[] labeledPatches, ProgressMonitor pm) throws IOException {
        return restClient.trainAndClassify(classifierName, prePopulate, labeledPatches);
    }

    @Override
    public Patch[] getMostAmbigous(boolean prePopulate, ProgressMonitor pm) throws IOException {
        return restClient.getMostAmbigous(classifierName, prePopulate);    }

    @Override
    public int getNumIterations() {
        return model.getNumIterations();
    }

    @Override
    public URI getPatchQuicklookUri(Patch patch, String quicklookBandName) throws IOException {
        return restClient.getPatchQuicklookUri(classifierName, patch, quicklookBandName);
    }

    @Override
    public BufferedImage getPatchQuicklook(Patch patch, String quicklookBandName) throws IOException {
        return restClient.getPatchQuicklook(classifierName, patch, quicklookBandName);
    }

    @Override
    public File getPatchProductFile(Patch patch) throws IOException {
        return null;
    }

}
