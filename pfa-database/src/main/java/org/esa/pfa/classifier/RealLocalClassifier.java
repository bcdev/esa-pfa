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
import org.esa.pfa.db.PatchQuery;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.fe.PatchAccess;
import org.esa.pfa.fe.op.Patch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A local implementation
 */
public class RealLocalClassifier implements RealClassifier {

    private static final int NUM_TRAINING_IMAGES_DEFAULT = 12;
    private static final int NUM_RETRIEVED_IMAGES_DEFAULT = 50;
    private static final int NUM_HITS_MAX = 500;

    private final Path classifierPath;
    private final ActiveLearning al;
    private final PFAApplicationDescriptor applicationDescriptor;
    private final PatchQuery db;
    private final PatchAccess patchAccess;

    private int numTrainingImages = NUM_TRAINING_IMAGES_DEFAULT;
    private int numRetrievedImages = NUM_RETRIEVED_IMAGES_DEFAULT;
    private ClassifierPersitable.PatchInfo[] queryPatchInfo = new ClassifierPersitable.PatchInfo[0];
    private ClassifierPersitable.PatchInfo[] trainingPatchInfo = new ClassifierPersitable.PatchInfo[0];


    public RealLocalClassifier(Path classifierPath, PFAApplicationDescriptor applicationDescriptor, Path patchPath, Path dbPath) throws IOException {
        this.classifierPath = classifierPath;
        this.applicationDescriptor = applicationDescriptor;
        this.al = new ActiveLearning();
        if (Files.exists(dbPath.resolve("ds-descriptor.xml"))) {
            db = new PatchQuery(dbPath.toFile(), applicationDescriptor.getDefaultFeatureSet());
            patchAccess = new PatchAccess(patchPath.toFile(), db.getEffectiveFeatureTypes());
        } else {
            // currently for test only
            db = null;
            patchAccess = null;
        }
    }

    @Override
    public int getNumTrainingImages() {
        return numTrainingImages;
    }

    @Override
    public void setNumTrainingImages(int numTrainingImages) {
        this.numTrainingImages = numTrainingImages;
    }

    @Override
    public int getNumRetrievedImages() {
        return numRetrievedImages;
    }

    @Override
    public void setNumRetrievedImages(int numRetrievedImages) {
        this.numRetrievedImages = numRetrievedImages;
    }

    @Override
    public void saveClassifier() throws IOException {
        final ClassifierPersitable persitable = new ClassifierPersitable(applicationDescriptor.getName(), numTrainingImages, numRetrievedImages, al);
        persitable.write(classifierPath.toFile());
    }


    static Classifier loadClassifier(String classifierName, Path classifierPath, Path patchPath, Path dbPath) throws IOException {
        if (!Files.exists(classifierPath)) {
            throw new IllegalArgumentException("Classifier does not exist. " + classifierName);
        }
        final ClassifierPersitable persitable = ClassifierPersitable.read(classifierPath.toFile());
        String applicationName = persitable.getApplicationName();
        PFAApplicationDescriptor applicationDescriptor = PFAApplicationRegistry.getInstance().getDescriptor(applicationName);


        RealLocalClassifier realLocalClassifier = new RealLocalClassifier(classifierPath, applicationDescriptor, patchPath, dbPath);
        realLocalClassifier.numTrainingImages = persitable.getNumTrainingImages();
        realLocalClassifier.numRetrievedImages = persitable.getNumRetrievedImages();

        // TODO this method re-trains the svm
        realLocalClassifier.initActiveLearningAfterLoading(persitable, ProgressMonitor.NULL); // TODO pm

        return new Classifier(classifierName, applicationDescriptor, realLocalClassifier);
    }

    private void initActiveLearningAfterLoading(ClassifierPersitable storedClassifier, ProgressMonitor pm) throws IOException {
        al.setModel(storedClassifier.getModel());

        final Patch[] queryPatches = loadPatches(storedClassifier.getQueryPatchInfo());
        if (queryPatches != null && queryPatches.length > 0) {
            al.setQueryPatches(queryPatches);
        }

        final Patch[] patches = loadPatches(storedClassifier.getTrainingPatchInfo());
        if (patches != null && patches.length > 0) {
            al.setTrainingData(patches, storedClassifier.getNumIterations(), pm);
        }
    }

    private Patch[] loadPatches(final ClassifierPersitable.PatchInfo[] patchInfo) throws IOException {
        if (patchInfo != null && patchInfo.length > 0) {
            final Patch[] patches = new Patch[patchInfo.length];
            int i = 0;
            for (ClassifierPersitable.PatchInfo info : patchInfo) {
                patches[i++] = patchAccess.loadPatch(info.parentProductName, info.patchX, info.patchY, info.label);
            }
            return patches;
        }
        return null;
    }

    @Override
    public void startTraining(Patch[] queryPatches, ProgressMonitor pm) throws IOException {
        al.resetQuery();
        al.setQueryPatches(queryPatches);
        populateArchivePatches(pm);
        saveClassifier();
    }

    private void populateArchivePatches(final ProgressMonitor pm) {
        final Patch[] archivePatches = db.query(applicationDescriptor.getAllQueryExpr(), NUM_HITS_MAX);

        int numFeaturesQuery = al.getQueryPatches()[0].getFeatures().length;
        int numFeaturesDB = archivePatches[0].getFeatures().length;
        if (numFeaturesDB != numFeaturesQuery) {
            String msg = String.format("Incompatible Database.\n" +
                                               "The patches in the database have %d features.\n" +
                                               "The query patches have %d features.", numFeaturesDB, numFeaturesQuery);
            throw new IllegalArgumentException(msg);
        }

        al.setRandomPatches(archivePatches, pm);
    }

    @Override
    public Patch[] getMostAmbigousPatches(ProgressMonitor pm) {
        return al.getMostAmbiguousPatches(numTrainingImages, pm);
    }

    @Override
    public void train(Patch[] labeledPatches, ProgressMonitor pm) throws IOException {
        al.train(labeledPatches, pm);
        saveClassifier();
    }

    @Override
    public Patch[] classify() {
        final Patch[] archivePatches = db.query(applicationDescriptor.getAllQueryExpr(), numRetrievedImages * 10);
        al.classify(archivePatches);
        final List<Patch> relavantImages = new ArrayList<>(numRetrievedImages);
        for (int i = 0; i < archivePatches.length && relavantImages.size() < numRetrievedImages; i++) {
            if (archivePatches[i].getLabel() == Patch.Label.RELEVANT) {
                relavantImages.add(archivePatches[i]);
            }
        }
        return relavantImages.toArray(new Patch[relavantImages.size()]);
    }
}
