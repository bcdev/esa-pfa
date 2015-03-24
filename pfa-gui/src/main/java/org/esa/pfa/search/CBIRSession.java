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
import com.bc.ceres.core.SubProgressMonitor;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.ordering.ProductOrderBasket;
import org.esa.pfa.ordering.ProductOrderService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Encapsulates the state of a user's CBIR session.
 */
public class CBIRSession {

    public enum Notification {
        NewClassifier,
        DeleteClassifier,
        NewTrainingImages,
        ModelTrained
    }

    private static CBIRSession instance = null;

    private final ProductOrderBasket productOrderBasket;

    private final ProductOrderService productOrderService;

    private final List<Patch> relevantImageList = new ArrayList<>(50);
    private final List<Patch> irrelevantImageList = new ArrayList<>(50);
    private final List<Patch> retrievedImageList = new ArrayList<>(500);

    private final List<Listener> listenerList = new ArrayList<>(1);

    private Classifier classifier;
    private String quicklookBandName1;
    private String quicklookBandName2;

    public enum ImageMode { SINGLE, DUAL, FADE }
    private ImageMode imageMode = ImageMode.SINGLE;

    private CBIRSession() {
        productOrderBasket = new ProductOrderBasket();
        productOrderService = new ProductOrderService(productOrderBasket);
    }

    public static CBIRSession getInstance() {
        if (instance == null) {
            instance = new CBIRSession();
        }
        return instance;
    }

    public boolean hasClassifier() {
        return classifier != null;
    }

    public Classifier getClassifier() {
        return classifier;
    }

    public void createClassifier(final String classifierName,
                                 final PFAApplicationDescriptor applicationDescriptor,
                                 final String dbFolder,
                                 final ProgressMonitor pm) throws Exception {
        try {
            quicklookBandName1 = applicationDescriptor.getDefaultQuicklookFileName();
            quicklookBandName2 = quicklookBandName1;
            classifier = new Classifier(applicationDescriptor, dbFolder, classifierName);
            classifier.saveClassifier();
            clearPatchLists();
            fireNotification(Notification.NewClassifier, classifier);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public void loadClassifier(String dbFolder, String classifierName) throws Exception {
        try {
            classifier = Classifier.loadClassifier(dbFolder, classifierName, ProgressMonitor.NULL);
            clearPatchLists();
            fireNotification(Notification.NewClassifier, classifier);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    private void clearPatchLists() {
        relevantImageList.clear();
        irrelevantImageList.clear();
        retrievedImageList.clear();
    }

    public void deleteClassifier() throws Exception {
        try {
            Classifier deletedClassifier = classifier;
            classifier.deleteClassifier();
            classifier = null;
            fireNotification(Notification.DeleteClassifier, deletedClassifier);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public void setImageMode(final ImageMode mode) {
        imageMode = mode;
    }

    public ImageMode getImageMode() {
        return imageMode;
    }

    public String getClassifierName() {
        return classifier.getClassifierName();
    }

    public PFAApplicationDescriptor getApplicationDescriptor() {
        return classifier.getApplicationDescriptor();
    }

    public ProductOrderBasket getProductOrderBasket() {
        return productOrderBasket;
    }

    public ProductOrderService getProductOrderService() {
        return productOrderService;
    }

    public FeatureType[] getEffectiveFeatureTypes() {
        return classifier.getEffectiveFeatureTypes();
    }

    public void setNumTrainingImages(final int numTrainingImages) throws Exception {
        classifier.setNumTrainingImages(numTrainingImages);
    }

    public int getNumTrainingImages() {
        return classifier.getNumTrainingImages();
    }

    public void setNumRetrievedImages(final int numRetrievedImages) throws Exception {
        classifier.setNumRetrievedImages(numRetrievedImages);
    }

    public int getNumRetrievedImages() {
        return classifier.getNumRetrievedImages();
    }

    public int getNumIterations() {
        return classifier.getNumIterations();
    }

    public static String[] getSavedClassifierNames(final String archiveFolder) {
        return Classifier.getSavedClassifierNames(archiveFolder);
    }

    public String getQuicklookBandName1() {
        return quicklookBandName1;
    }

    public void setQuicklookBandName1(final String quicklookBandName) {
        this.quicklookBandName1 = quicklookBandName;
    }

    public String getQuicklookBandName2() {
        return quicklookBandName2;
    }

    public void setQuicklookBandName2(final String quicklookBandName) {
        this.quicklookBandName2 = quicklookBandName;
    }

    public String[] getAvailableQuickLooks(final Patch patch) throws IOException {
        return classifier.getAvailableQuickLooks(patch);
    }

    public void addQueryPatch(final Patch patch) {
        classifier.addQueryImage(patch);
    }

    public Patch[] getQueryPatches() {
        return classifier.getQueryImages();
    }

    public void setQueryImages(final Patch[] queryImages, final ProgressMonitor pm) throws Exception {
        pm.beginTask("Getting Images to Label", 100);
        try {
            classifier.setQueryImages(queryImages, SubProgressMonitor.create(pm, 50));
            getImagesToLabel(SubProgressMonitor.create(pm, 50));
        } finally {
            pm.done();
        }
    }

    public void populateArchivePatches(final ProgressMonitor pm) throws Exception {
        classifier.populateArchivePatches(pm);
    }

    public void reassignTrainingImage(final Patch patch) {
        if (patch.getLabel() == Patch.LABEL_RELEVANT) {
            int index = irrelevantImageList.indexOf(patch);
            if (index != -1) {
                irrelevantImageList.remove(index);
                relevantImageList.add(patch);
            }
        } else if (patch.getLabel() == Patch.LABEL_IRRELEVANT) {
            int index = relevantImageList.indexOf(patch);
            if (index != -1) {
                relevantImageList.remove(index);
                irrelevantImageList.add(patch);
            }
        }
    }

    public Patch[] getRelevantTrainingImages() {
        return relevantImageList.toArray(new Patch[relevantImageList.size()]);
    }

    public Patch[] getIrrelevantTrainingImages() {
        return irrelevantImageList.toArray(new Patch[irrelevantImageList.size()]);
    }

    /**
     * Not all patches need quicklooks. This function adds quicklooks to the patches requested
     *
     * @param patch the patches to get quicklooks for
     * @param quicklookBandName the quicklook to retrieve
     */
    public void getPatchQuicklook(final Patch patch, final String quicklookBandName) {
        classifier.getPatchQuicklook(patch, quicklookBandName);
    }

    public void getImagesToLabel(final ProgressMonitor pm) throws Exception {

        relevantImageList.clear();
        irrelevantImageList.clear();

        final Patch[] imagesToLabel = classifier.getImagesToLabel(pm);
        for (Patch patch : imagesToLabel) {
            if (patch.getLabel() == Patch.LABEL_RELEVANT) {
                relevantImageList.add(patch);
            } else {
                // default to irrelevant so user only needs to select the relevant
                patch.setLabel(Patch.LABEL_IRRELEVANT);
                irrelevantImageList.add(patch);
            }
        }
        if (!pm.isCanceled()) {
            fireNotification(Notification.NewTrainingImages, classifier);
        }
    }

    public void trainModel(final ProgressMonitor pm) throws Exception {
        final List<Patch> labeledList = new ArrayList<Patch>(30);
        labeledList.addAll(relevantImageList);
        labeledList.addAll(irrelevantImageList);

        classifier.trainModel(labeledList.toArray(new Patch[labeledList.size()]), pm);

        fireNotification(Notification.ModelTrained, classifier);
    }

    public void retrieveImages() throws Exception {
        retrievedImageList.clear();
        retrievedImageList.addAll(Arrays.asList(classifier.getRetrievedImages()));
    }

    public Patch[] getRetrievedImages() {
        return retrievedImageList.toArray(new Patch[retrievedImageList.size()]);
    }

    private void fireNotification(final Notification msg, final Classifier classifier) {
        for (Listener listener : listenerList) {
            listener.notifySessionMsg(msg, classifier);
        }
    }

    public void addListener(final Listener listener) {
        if (!listenerList.contains(listener)) {
            listenerList.add(listener);
        }
    }

    public interface Listener {
        void notifySessionMsg(final Notification msg, Classifier classifier);
    }
}
