/*
 * Copyright (C) 2013 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.pfa.search;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.util.io.FileUtils;
import org.esa.pfa.activelearning.ActiveLearning;
import org.esa.pfa.activelearning.ClassifierPersitable;
import org.esa.pfa.db.PatchQuery;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.fe.PatchAccess;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Stub for PFA Search Tool on the server.
 * TODO major refactorings required (after demo):
 * DONE (1) rename this to Classifier, because it is *the* classifier.
 * (2) introduce ClassifierService interface, which is used by CBIRSession. ClassifierService manages Classifiers (local or remote ones)
 */
public class Classifier {

    private final PFAApplicationDescriptor applicationDescriptor;
    private final String auxDbPath;
    private final String classifierName;
    private final PatchQuery db;
    private final ActiveLearning al;
    private final PatchAccess patchAccess;

    private int numTrainingImages = 12;
    private int numRetrievedImages = 50;
    private int numHitsMax = 500;

    public Classifier(final PFAApplicationDescriptor applicationDescriptor,
                      final String auxDbPath,
                      final String classifierName) throws Exception {
        this.applicationDescriptor = applicationDescriptor;
        this.auxDbPath = auxDbPath;
        this.classifierName = classifierName;

        File datasetDir = new File(auxDbPath);
        db = new PatchQuery(datasetDir, applicationDescriptor.getDefaultFeatureSet());
        al = new ActiveLearning();
        patchAccess = new PatchAccess(datasetDir, db.getEffectiveFeatureTypes());
    }

    public String getClassifierName() {
        return classifierName;
    }

    public PatchAccess getPatchAccess() {
        return patchAccess;
    }

    public PFAApplicationDescriptor getApplicationDescriptor() {
        return applicationDescriptor;
    }

    public FeatureType[] getEffectiveFeatureTypes() {
        return db.getEffectiveFeatureTypes();
    }

    public void deleteClassifier() throws IOException {
        //todo other clean up
        File classifierFile = getClassifierFile(auxDbPath, classifierName);
        if (classifierFile.exists()) {
            boolean delete = classifierFile.delete();
            if (!delete) {
                throw new IOException("Failed to delete " + classifierFile);
            }
        }
    }

    public void setNumTrainingImages(final int numTrainingImages) throws Exception {
        this.numTrainingImages = numTrainingImages;
        saveClassifier();
    }

    public int getNumTrainingImages() {
        return numTrainingImages;
    }

    public void setNumRetrievedImages(final int numRetrievedImages) throws Exception {
        this.numRetrievedImages = numRetrievedImages;
        saveClassifier();
    }

    public int getNumRetrievedImages() {
        return numRetrievedImages;
    }

    public int getNumIterations() {
        return al.getNumIterations();
    }

    public void addQueryImage(final Patch patch) {
        al.addQueryImage(patch);
    }

    public Patch[] getQueryImages() {
        return al.getQueryPatches();
    }

    public void populateArchivePatches(final ProgressMonitor pm) throws Exception {
        final Patch[] archivePatches = db.query(applicationDescriptor.getAllQueryExpr(), numHitsMax);

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

    public void setQueryImages(final Patch[] queryPatches, final ProgressMonitor pm) throws Exception {

        al.resetQuery();
        al.setQueryPatches(queryPatches);
        populateArchivePatches(pm);

        saveClassifier();
    }

    public Patch[] getImagesToLabel(final ProgressMonitor pm) throws Exception {
        return al.getMostAmbiguousPatches(numTrainingImages, pm);
    }

    public String[] getAvailableQuickLooks(final Patch patch) throws IOException {
        return patchAccess.getAvailableQuickLooks(patch);
    }

    /**
     * Not all patches need quicklooks. This function adds quicklooks to the patches requested
     *
     * @param patch the patches to get quicklooks for
     * @param quicklookBandName the quicklook to retrieve
     */
    public void getPatchQuicklook(final Patch patch, final String quicklookBandName) {

        if (patch.getImage(quicklookBandName) == null) {
            try {
                URL imageURL = patchAccess.retrievePatchImage(patch, quicklookBandName);
                //todo download image
                File imageFile = new File(imageURL.getPath());
                BufferedImage img = loadImageFile(imageFile);
                patch.setImage(quicklookBandName, img);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void trainModel(final Patch[] labeledImages, final ProgressMonitor pm) throws Exception {
        al.train(labeledImages, pm);

        saveClassifier();
    }

    public Patch[] getRetrievedImages() throws Exception {

        final List<Patch> relavantImages = new ArrayList<>(numRetrievedImages);
        final Patch[] archivePatches = db.query(applicationDescriptor.getAllQueryExpr(), numRetrievedImages * 10);
        al.classify(archivePatches);
        int i = 0;
        for (Patch patch : archivePatches) {
            if (patch.getLabel() == Patch.LABEL_RELEVANT) {
                if (!contains(relavantImages, patch)) {
                    relavantImages.add(patch);
                    ++i;
                }
                if (i >= numRetrievedImages) {
                    break;
                }
            }
        }
        return relavantImages.toArray(new Patch[relavantImages.size()]);
    }

    private static boolean contains(final List<Patch> list, final Patch patch) {
        //todo
        return false;
    }

    private static BufferedImage loadImageFile(final File file) {
        BufferedImage bufferedImage = null;
        if (file.canRead()) {
            try {
                try (BufferedInputStream fis = new BufferedInputStream(new FileInputStream(file))) {
                    bufferedImage = ImageIO.read(fis);
                }
            } catch (Exception e) {
                e.printStackTrace();
                //
            }
        }
        return bufferedImage;
    }

    public static String[] getSavedClassifierNames(final String auxDbPath) {
        final List<String> nameList = new ArrayList<>(10);
        final File classifierFolder = getClassifierDir(auxDbPath);
        if (!classifierFolder.exists()) {
            classifierFolder.mkdirs();
        }
        final File[] files = classifierFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".xml");
            }
        });
        if (files != null) {
            for (File file : files) {
                nameList.add(FileUtils.getFilenameWithoutExtension(file));
            }
        }
        return nameList.toArray(new String[nameList.size()]);
    }

    static Classifier loadClassifier(String auxDbPath, String classifierName, ProgressMonitor pm) throws Exception {

        File classifierFile = getClassifierFile(auxDbPath, classifierName);

        final ClassifierPersitable storedClassifier = ClassifierPersitable.read(classifierFile);
        String applicationName = storedClassifier.getApplicationName();
        PFAApplicationDescriptor applicationDescriptor = PFAApplicationRegistry.getInstance().getDescriptor(applicationName);

        Classifier classifier = new Classifier(applicationDescriptor, auxDbPath, classifierName);
        classifier.numTrainingImages = storedClassifier.getNumTrainingImages();
        classifier.numRetrievedImages = storedClassifier.getNumRetrievedImages();
        classifier.initActiveLearning(storedClassifier, pm);
        return classifier;
    }

    private static File getClassifierFile(String auxDbPath, String classifierName) {
        File classifierDir = getClassifierDir(auxDbPath);
        return new File(classifierDir, classifierName + ".xml");
    }

    private static File getClassifierDir(String auxDbPath) {
        File auxDbDir = new File(auxDbPath);
        return new File(auxDbDir, "Classifiers");
    }

    private void initActiveLearning(ClassifierPersitable storedClassifier, ProgressMonitor pm) throws Exception {
        al.setModel(storedClassifier.getModel());

        final Patch[] queryPatches = loadPatches(storedClassifier.getQueryPatchInfo());
        if (queryPatches != null && queryPatches.length > 0) {
            al.setQueryPatches(queryPatches);
        }

        final Patch[] patches = loadPatches(storedClassifier.getPatchInfo());
        if (patches != null && patches.length > 0) {
            al.setTrainingData(patches, storedClassifier.getNumIterations(), pm);
        }
    }

    private Patch[] loadPatches(final ClassifierPersitable.PatchInfo[] patchInfo) throws Exception {
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

    void saveClassifier() throws IOException {
        File classifierDir = getClassifierDir(auxDbPath);
        if (classifierDir != null && !classifierDir.exists()) {
            classifierDir.mkdirs();
        }
        final ClassifierPersitable writer = new ClassifierPersitable(applicationDescriptor.getName(), numTrainingImages, numRetrievedImages, al);
        File classifierFile = getClassifierFile(auxDbPath, classifierName);
        writer.write(classifierFile);
    }
}
