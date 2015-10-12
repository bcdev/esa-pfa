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
import org.esa.pfa.db.DsIndexerTool;
import org.esa.pfa.db.PatchQuery;
import org.esa.pfa.fe.AbstractApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.fe.op.DatasetDescriptor;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;
import org.esa.snap.core.util.SystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A local implementation
 */
public class LocalClassifier implements Classifier {

    private static final int NUM_HITS_MAX = 500;

    private final Path classifierPath;
    private final PFAApplicationDescriptor applicationDescriptor;
    private final ActiveLearning al;
    private final PatchQuery db;

    private final ClassifierModel model;
    private final String classifierName;

    public LocalClassifier(String name, ClassifierModel model, Path classifierPath, PFAApplicationDescriptor applicationDescriptor, Path dbPath) throws IOException {
        this.classifierName = name;
        this.model = model;
        this.classifierPath = classifierPath;
        this.applicationDescriptor = applicationDescriptor;
        this.al = new ActiveLearning(model);
        if (Files.exists(dbPath.resolve("ds-descriptor.xml")) && Files.exists(dbPath.resolve(DsIndexerTool.DEFAULT_INDEX_NAME))) {
            DatasetDescriptor dsDescriptor = DatasetDescriptor.read(new File(dbPath.toFile(), "ds-descriptor.xml"));
            Set<String> defaultFeatureSet = applicationDescriptor.getDefaultFeatureSet();
            FeatureType[] featureTypes = dsDescriptor.getFeatureTypes();
            FeatureType[] effectiveFeatureTypes = AbstractApplicationDescriptor.getEffectiveFeatureTypes(featureTypes, defaultFeatureSet);
            db = new PatchQuery(dbPath.toFile(), dsDescriptor, effectiveFeatureTypes);
        } else {
            SystemUtils.LOG.severe("LocalClassifier database not found");
            // currently for test only
            db = null;
        }
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
    public int getNumIterations() {
        return model.getNumIterations();
    }

    public void saveClassifier() throws IOException {
        model.toFile(classifierPath.toFile());
    }


    public static LocalClassifier loadClassifier(String classifierName, Path classifierPath, Path dbPath) throws IOException {
        if (!Files.exists(classifierPath)) {
            throw new IllegalArgumentException("Classifier does not exist. " + classifierName);
        }
        ClassifierModel classifierModel = ClassifierModel.fromFile(classifierPath.toFile());
        PFAApplicationDescriptor applicationDescriptor = PFAApplicationRegistry.getInstance().getDescriptorByName(classifierModel.getApplicationName());

        LocalClassifier localClassifier = new LocalClassifier(classifierName, classifierModel, classifierPath, applicationDescriptor, dbPath);
        localClassifier.al.setTrainingData(ProgressMonitor.NULL);

        return localClassifier;
    }

    @Override
    public Patch[] startTraining(Patch[] queryPatches, ProgressMonitor pm) throws IOException {
        pm.beginTask("start training", 100);
        try {
            al.resetQuery();
            al.setQueryPatches(queryPatches);
            populateArchivePatches(SubProgressMonitor.create(pm, 50));
            return al.getMostAmbiguousPatches(model.getNumTrainingImages(), SubProgressMonitor.create(pm, 50));
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
            final List<Patch> relavantImages = new ArrayList<>(model.getNumRetrievedImages());
            while (relavantImages.size() < model.getNumRetrievedImages() && classifiedImages < model.getNumRetrievedImages() * 100) {
                final Patch[] archivePatches = db.getRandomPatches(model.getNumRetrievedImages());
                classifiedImages += archivePatches.length;
                al.classify(archivePatches);
                for (int i = 0; i < archivePatches.length && relavantImages.size() < model.getNumRetrievedImages(); i++) {
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
            return al.getMostAmbiguousPatches(model.getNumTrainingImages(), SubProgressMonitor.create(pm, 50));
        } finally {
            saveClassifier();
            pm.done();
        }
    }

    private void populateArchivePatches(final ProgressMonitor pm) {
        final Patch[] archivePatches = db.getRandomPatches(NUM_HITS_MAX);

        if(archivePatches.length > 0) {
            int numFeaturesQuery = model.getQueryData().get(0).getFeatureValues().length;
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
