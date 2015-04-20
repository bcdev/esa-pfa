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
import org.esa.pfa.activelearning.ActiveLearning;
import org.esa.pfa.activelearning.ClassifierPersitable;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.op.Patch;

import java.io.IOException;

/**
 * Created by marcoz on 17.04.15.
 */
public class Classifier {

    private static final int NUM_TRAINING_IMAGES_DEFAULT = 12;
    private static final int NUM_RETRIEVED_IMAGES_DEFAULT = 50;

    private final String name;
    private final PFAApplicationDescriptor applicationDescriptor;
    private final ClassifierService classifierService;
    private final ActiveLearning al;

    private int numTrainingImages = NUM_TRAINING_IMAGES_DEFAULT;
    private int numRetrievedImages = NUM_RETRIEVED_IMAGES_DEFAULT;
    private ClassifierPersitable.PatchInfo[] queryPatchInfo;
    private ClassifierPersitable.PatchInfo[] trainingPatchInfo;

    public Classifier(String name, PFAApplicationDescriptor applicationDescriptor, ClassifierService classifierService) {
        this.name = name;
        this.applicationDescriptor = applicationDescriptor;
        this.classifierService = classifierService;
        this.al = new ActiveLearning();
    }

    void startTraining(Patch[] queryPatches) {
//
//        al.resetQuery();
//        al.setQueryPatches(queryPatches);
//        populateArchivePatches(pm);
//
//        saveClassifier();

        classifierService.startTraining(queryPatches);
    }

//    private void populateArchivePatches(final ProgressMonitor pm) throws Exception {
//        final Patch[] archivePatches = db.query(applicationDescriptor.getAllQueryExpr(), numHitsMax);
//
//        int numFeaturesQuery = al.getQueryPatches()[0].getFeatures().length;
//        int numFeaturesDB = archivePatches[0].getFeatures().length;
//        if (numFeaturesDB != numFeaturesQuery) {
//            String msg = String.format("Incompatible Database.\n" +
//                                               "The patches in the database have %d features.\n" +
//                                               "The query patches have %d features.", numFeaturesDB, numFeaturesQuery);
//            throw new IllegalArgumentException(msg);
//        }
//
//        al.setRandomPatches(archivePatches, pm);
//    }

    Patch[] getMostAmbigousPatches() {
        return classifierService.getmostAmbigous(numTrainingImages);
    }

    void train(Patch[] labeledPatches) {
        classifierService.train(labeledPatches);
    }

    Patch[] getBestPatches() {
        return classifierService.getBestPatches();
    }

    String getName() {
        return name;
    }

    public PFAApplicationDescriptor getApplicationDescriptor() {
        return applicationDescriptor;
    }

    public ActiveLearning getActiveLearning() {
        return al;
    }

    public int getNumTrainingImages() {
        return numTrainingImages;
    }

    public void setNumTrainingImages(int numTrainingImages) throws IOException {
        this.numTrainingImages = numTrainingImages;
        classifierService.save(this);
    }

    public int getNumRetrievedImages() {
        return numRetrievedImages;
    }

    public void setNumRetrievedImages(int numRetrievedImages) throws IOException {
        this.numRetrievedImages = numRetrievedImages;
        classifierService.save(this);
    }

    public void setQueryPatchInfo(ClassifierPersitable.PatchInfo[] queryPatchInfo) {
        this.queryPatchInfo = queryPatchInfo;
    }

    public void setTrainingPatchInfo(ClassifierPersitable.PatchInfo[] trainingPatchInfo) {
        this.trainingPatchInfo = trainingPatchInfo;
    }
}
