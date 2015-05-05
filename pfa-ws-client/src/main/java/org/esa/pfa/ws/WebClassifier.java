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
import org.esa.pfa.activelearning.ActiveLearning;
import org.esa.pfa.classifier.Classifier;
import org.esa.pfa.classifier.ClassifierModel;
import org.esa.pfa.classifier.LocalClassifier;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by marcoz on 24.04.15.
 */
public class WebClassifier implements Classifier {


    private final String classifierName;
    private ClassifierModel model;
    private ActiveLearning al;
    private final RestClient restClient;


    public WebClassifier(String classifierName, ClassifierModel model, RestClient restClient) {
        this.classifierName = classifierName;
        this.model = model;
        this.restClient = restClient;
        this.al = new ActiveLearning(model);
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
    public void saveClassifier() throws IOException {
        // TODO

    }

    @Override
    public void startTraining(Patch[] queryPatches, ProgressMonitor pm) throws IOException {
        al.resetQuery();
        al.setQueryPatches(queryPatches);
        String modelXML = model.toXML();

//        populateArchivePatches(pm);
//        saveClassifier();
        String newModelXML = restClient.populateArchivePatches(classifierName, modelXML);
        model = ClassifierModel.fromXML(newModelXML);
        al = new ActiveLearning(model);
        al.setTrainingData(pm);
    }

    @Override
    public Patch[] getMostAmbigousPatches(ProgressMonitor pm) {
        return new Patch[0];
    }

    @Override
    public void train(Patch[] labeledPatches, ProgressMonitor pm) throws IOException {

    }

    @Override
    public Patch[] classify() {
        return new Patch[0];
    }

    @Override
    public int getNumIterations() {
        return model.getNumIterations();
    }

    @Override
    public FeatureType[] getEffectiveFeatureTypes() {
        return new FeatureType[0];
    }

    @Override
    public void populateArchivePatches(ProgressMonitor pm) {

    }

    @Override
    public void getPatchQuicklook(Patch patch, String quicklookBandName) {

    }

    @Override
    public File getPatchProductFile(Patch patch) throws IOException {
        return null;
    }

    @Override
    public void addQueryPatch(Patch patch) {
        model.getQueryData().add(patch);
    }

    @Override
    public Patch[] getQueryPatches() {
        List<Patch> queryData = model.getQueryData();
        return queryData.toArray(new Patch[queryData.size()]);
    }
}
