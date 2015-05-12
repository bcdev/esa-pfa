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
import org.esa.pfa.fe.AbstractApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.fe.PatchAccess;
import org.esa.pfa.fe.op.DatasetDescriptor;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;
import org.esa.snap.util.SystemUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
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
    private final ActiveLearning al;
    private final PFAApplicationDescriptor applicationDescriptor;
    private final PatchQuery db;
    private final PatchAccess patchAccess;

    private final ClassifierModel model;
    private final String classifierName;

    public LocalClassifier(String name, ClassifierModel model, Path classifierPath, PFAApplicationDescriptor applicationDescriptor, Path patchPath, Path dbPath) throws IOException {
        this.classifierName = name;
        this.model = model;
        this.classifierPath = classifierPath;
        this.applicationDescriptor = applicationDescriptor;
        this.al = new ActiveLearning(model);
        if (Files.exists(dbPath.resolve("ds-descriptor.xml"))) {
            DatasetDescriptor dsDescriptor = DatasetDescriptor.read(new File(dbPath.toFile(), "ds-descriptor.xml"));
            Set<String> defaultFeatureSet = applicationDescriptor.getDefaultFeatureSet();
            FeatureType[] featureTypes = dsDescriptor.getFeatureTypes();
            FeatureType[] effectiveFeatureTypes = AbstractApplicationDescriptor.getEffectiveFeatureTypes(featureTypes, defaultFeatureSet);
            db = new PatchQuery(dbPath.toFile(), dsDescriptor, effectiveFeatureTypes);
            patchAccess = new PatchAccess(patchPath.toFile());
        } else {
            SystemUtils.LOG.severe("LocalClassifier database not found");
            // currently for test only
            db = null;
            patchAccess = null;
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


    public static LocalClassifier loadClassifier(String classifierName, Path classifierPath, Path patchPath, Path dbPath) throws IOException {
        if (!Files.exists(classifierPath)) {
            throw new IllegalArgumentException("Classifier does not exist. " + classifierName);
        }
        ClassifierModel classifierModel = ClassifierModel.fromFile(classifierPath.toFile());
        PFAApplicationDescriptor applicationDescriptor = PFAApplicationRegistry.getInstance().getDescriptorByName(classifierModel.getApplicationName());

        LocalClassifier localClassifier = new LocalClassifier(classifierName, classifierModel, classifierPath, applicationDescriptor, patchPath, dbPath);
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
            if (prePopulate) {
                populateArchivePatches(SubProgressMonitor.create(pm, 50));
            }
            al.train(labeledPatches, SubProgressMonitor.create(pm, 50));
            final Patch[] archivePatches = db.query(applicationDescriptor.getAllQueryExpr(), model.getNumRetrievedImages() * 10);
            al.classify(archivePatches);
            final List<Patch> relavantImages = new ArrayList<>(model.getNumRetrievedImages());
            for (int i = 0; i < archivePatches.length && relavantImages.size() < model.getNumRetrievedImages(); i++) {
                if (archivePatches[i].getLabel() == Patch.Label.RELEVANT) {
                    relavantImages.add(archivePatches[i]);
                }
            }
            return relavantImages.toArray(new Patch[relavantImages.size()]);
        } finally {
            saveClassifier();
            pm.done();
        }
    }

    @Override
    public Patch[] getMostAmbigous(boolean prePopulate, ProgressMonitor pm) throws IOException {
        pm.beginTask("train and classify", 100);
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
        final Patch[] archivePatches = db.query(applicationDescriptor.getAllQueryExpr(), NUM_HITS_MAX);

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

    @Override
    public URI getPatchQuicklookUri(Patch patch, String quicklookBandName) throws IOException {
        Path patchImagePath = patchAccess.getPatchImagePath(patch.getParentProductName(), patch.getPatchX(), patch.getPatchY(), quicklookBandName);
        if (patchImagePath != null) {
            return patchImagePath.toUri();
        } else {
            return null;
        }
    }

    @Override
    public BufferedImage getPatchQuicklook(Patch patch, String quicklookBandName) throws IOException {
        Path patchImagePath = patchAccess.getPatchImagePath(patch.getParentProductName(), patch.getPatchX(), patch.getPatchY(), quicklookBandName);
        if (patchImagePath != null) {
            return ImageIO.read(patchImagePath.toUri().toURL());
        } else {
            return null;
        }
    }

    @Override
    public File getPatchProductFile(Patch patch) throws IOException {
        return patchAccess.getPatchProductFile(patch);
    }

    @Override
    public String getFeaturesAsText(Patch patch) throws IOException {
        return patchAccess.getFeaturesAsText(patch.getParentProductName(), patch.getPatchX(), patch.getPatchY());
    }
}
