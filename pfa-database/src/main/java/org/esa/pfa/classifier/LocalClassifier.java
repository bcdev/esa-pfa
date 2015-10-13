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
import com.bc.ceres.core.SubProgressMonitor;
import org.esa.pfa.activelearning.ActiveLearning;
import org.esa.pfa.db.PatchQuery;
import org.esa.pfa.fe.op.Patch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A local implementation
 */
public class LocalClassifier implements Classifier {

    private static final int NUM_HITS_MAX = 500;

    private final Path classifierPath;
    private final ActiveLearning al;
    private final PatchQuery patchQuery;

    private final ClassifierModel classifierModel;
    private final String classifierName;

    public LocalClassifier(String name, ClassifierModel classifierModel, Path classifierPath, PatchQuery patchQuery) throws IOException {
        this.classifierName = name;
        this.classifierModel = classifierModel;
        this.classifierPath = classifierPath;
        this.patchQuery = patchQuery;
        this.al = new ActiveLearning(classifierModel);
        if (!classifierModel.getTrainingData().isEmpty()) {
            al.setTrainingData(ProgressMonitor.NULL);
        }
    }

    @Override
    public String getName() {
        return classifierName;
    }

    @Override
    public int getNumTrainingImages() {
        return classifierModel.getNumTrainingImages();
    }

    @Override
    public void setNumTrainingImages(int numTrainingImages) {
        classifierModel.setNumTrainingImages(numTrainingImages);
    }

    @Override
    public int getNumRetrievedImages() {
        return classifierModel.getNumRetrievedImages();
    }

    @Override
    public void setNumRetrievedImages(int numRetrievedImages) {
        classifierModel.setNumRetrievedImages(numRetrievedImages);
    }

    @Override
    public int getNumIterations() {
        return classifierModel.getNumIterations();
    }

    public void saveClassifier() throws IOException {
        classifierModel.toFile(classifierPath.toFile());
    }


    @Override
    public Patch[] startTraining(Patch[] queryPatches, ProgressMonitor pm) throws IOException {
        pm.beginTask("start training", 100);
        try {
            al.resetQuery();
            al.setQueryPatches(queryPatches);
            populateArchivePatches(SubProgressMonitor.create(pm, 50));
            return al.getMostAmbiguousPatches(classifierModel.getNumTrainingImages(), SubProgressMonitor.create(pm, 50));
        } finally {
            saveClassifier();
            pm.done();
        }
    }

    @Override
    public Patch[] trainAndClassify(boolean prePopulate, Patch[] labeledPatches, ProgressMonitor pm) throws IOException {
        pm.beginTask("train and classify", 100);
        try {
            long t1 = System.currentTimeMillis();
            if (prePopulate) {
                populateArchivePatches(SubProgressMonitor.create(pm, 50));
            }
            al.train(labeledPatches, SubProgressMonitor.create(pm, 50));

            long t2 = System.currentTimeMillis();
            System.out.println("trainAndClassify.train    = " + (t2 - t1));

            int classifiedImages = 0;
            final List<Patch> relavantImages = new ArrayList<>(classifierModel.getNumRetrievedImages());
            while (relavantImages.size() < classifierModel.getNumRetrievedImages() && classifiedImages < classifierModel.getNumRetrievedImages() * 100) {
                final Patch[] archivePatches = patchQuery.getRandomPatches(classifierModel.getNumRetrievedImages());
                classifiedImages += archivePatches.length;
                al.classify(archivePatches);
                for (int i = 0; i < archivePatches.length && relavantImages.size() < classifierModel.getNumRetrievedImages(); i++) {
                    if (archivePatches[i].getLabel() == Patch.Label.RELEVANT) {
                        relavantImages.add(archivePatches[i]);
                    }
                }
            }
            long t3 = System.currentTimeMillis();
            System.out.println("relavantImages            = " + relavantImages.size());
            System.out.println("classifiedImages          = " + classifiedImages);
            System.out.println("trainAndClassify.classify = " + (t3-t2));
            return relavantImages.toArray(new Patch[relavantImages.size()]);
        } finally {
            saveClassifier();
            pm.done();
        }
    }

    @Override
    public Patch[] getMostAmbigous(boolean prePopulate, ProgressMonitor pm) throws IOException {
        pm.beginTask("get most ambigous", 100);
        try {
            if (prePopulate) {
                populateArchivePatches(SubProgressMonitor.create(pm, 50));
            }
            return al.getMostAmbiguousPatches(classifierModel.getNumTrainingImages(), SubProgressMonitor.create(pm, 50));
        } finally {
            saveClassifier();
            pm.done();
        }
    }

    private void populateArchivePatches(final ProgressMonitor pm) {
        final Patch[] archivePatches = patchQuery.getRandomPatches(NUM_HITS_MAX);

        if(archivePatches.length > 0) {
            int numFeaturesQuery = classifierModel.getQueryData().get(0).getFeatureValues().length;
            int numFeaturesDB = archivePatches[0].getFeatureValues().length;
            if (numFeaturesDB != numFeaturesQuery) {
                String msg = String.format("Incompatible Database.\n" +
                                                   "The patches in the database have %d features.\n" +
                                                   "The query patches have %d features.", numFeaturesDB, numFeaturesQuery);
                throw new IllegalArgumentException(msg);
            }

            al.setRandomPatches(archivePatches, pm);
        }
    }

}
