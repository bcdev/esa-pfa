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
import org.esa.pfa.classifier.ClassifierDelegate;
import org.esa.pfa.classifier.ClassifierManager;
import org.esa.pfa.classifier.LocalClassifierManager;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.ordering.ProductOrderBasket;
import org.esa.pfa.ordering.ProductOrderService;
import org.esa.pfa.ws.RestClassifierManagerClient;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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

    private ClassifierDelegate classifier;
    private ClassifierManager classifierManager;

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

    public ClassifierDelegate getClassifier() {
        return classifier;
    }

    public synchronized ClassifierManager getClassifierManager(String uriString) throws URISyntaxException, IOException {
        if (uriString.startsWith("http")) {
            // if HTTP URL: Web Service Client
            URI uri = new URI(uriString);
            if (classifierManager == null || !classifierManager.getURI().equals(uri)) {
                classifierManager = new RestClassifierManagerClient(uri, "AlgalBloom"); // TODO
            }
        } else {
            // if file URL
            URI uri;
            if (uriString.startsWith("file:")) {
                uri = new URI(uriString);
            } else {
                File file = new File(uriString);
                uri = file.toURI();
            }
            if (classifierManager == null || !classifierManager.getURI().equals(uri)) {
                classifierManager = new LocalClassifierManager(uri);
            }
        }
        return classifierManager;
    }

    public void createClassifier(String classifierName, PFAApplicationDescriptor applicationDescriptor) throws IOException {
        try {
            quicklookBandName1 = applicationDescriptor.getDefaultQuicklookFileName();
            quicklookBandName2 = quicklookBandName1;
            classifier = classifierManager.create(classifierName, applicationDescriptor.getName());
            clearPatchLists();
            fireNotification(Notification.NewClassifier, classifier);
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public void loadClassifier(String classifierName) throws IOException {
        try {
            if (classifierManager != null) {
                classifier = classifierManager.get(classifierName);
                clearPatchLists();
                fireNotification(Notification.NewClassifier, this.classifier);
            }
        } catch (IOException e) {
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
            ClassifierDelegate deletedClassifier = classifier;
            if (classifierManager != null) {
                classifierManager.delete(classifier.getName());
            }
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

    public void addQueryPatch(final Patch patch) {
        classifier.addQueryPatch(patch);
    }

    public Patch[] getQueryPatches() {
        return classifier.getQueryPatches();
    }

    public void setQueryImages(final Patch[] queryImages, final ProgressMonitor pm) throws Exception {
        pm.beginTask("Getting Images to Label", 100);
        try {
            classifier.startTraining(queryImages, SubProgressMonitor.create(pm, 50));
            getImagesToLabel(SubProgressMonitor.create(pm, 50));
        } finally {
            pm.done();
        }
    }

    public void populateArchivePatches(final ProgressMonitor pm) throws Exception {
        classifier.populateArchivePatches(pm);
    }

    public void reassignTrainingImage(final Patch patch) {
        if (patch.getLabel() == Patch.Label.RELEVANT) {
            int index = irrelevantImageList.indexOf(patch);
            if (index != -1) {
                irrelevantImageList.remove(index);
                relevantImageList.add(patch);
            }
        } else if (patch.getLabel() == Patch.Label.IRRELEVANT) {
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

        final Patch[] imagesToLabel = classifier.getMostAmbigousPatches(pm);
        for (Patch patch : imagesToLabel) {
            if (patch.getLabel() == Patch.Label.RELEVANT) {
                relevantImageList.add(patch);
            } else {
                // default to irrelevant so user only needs to select the relevant
                patch.setLabel(Patch.Label.IRRELEVANT);
                irrelevantImageList.add(patch);
            }
        }
        if (!pm.isCanceled()) {
            fireNotification(Notification.NewTrainingImages, classifier);
        }
    }

    public void trainModel(final ProgressMonitor pm) throws Exception {
        final List<Patch> labeledList = new ArrayList<>(30);
        labeledList.addAll(relevantImageList);
        labeledList.addAll(irrelevantImageList);

        classifier.train(labeledList.toArray(new Patch[labeledList.size()]), pm);

        fireNotification(Notification.ModelTrained, classifier);
    }

    public void retrieveImages() throws Exception {
        retrievedImageList.clear();
        retrievedImageList.addAll(Arrays.asList(classifier.classify()));
    }

    public Patch[] getRetrievedImages() {
        return retrievedImageList.toArray(new Patch[retrievedImageList.size()]);
    }

    private void fireNotification(final Notification msg, final ClassifierDelegate classifier) {
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
        void notifySessionMsg(final Notification msg, ClassifierDelegate classifier);
    }
}
