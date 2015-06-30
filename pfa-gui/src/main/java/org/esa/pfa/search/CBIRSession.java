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
import org.esa.pfa.classifier.Classifier;
import org.esa.pfa.classifier.ClassifierManager;
import org.esa.pfa.classifier.DatabaseManager;
import org.esa.pfa.classifier.LocalDatabaseManager;
import org.esa.pfa.fe.AbstractApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.ordering.ProductOrderBasket;
import org.esa.pfa.ordering.ProductOrderService;
import org.esa.pfa.ws.RestDatabaseManager;
import org.esa.snap.rcp.util.ContextGlobalExtender;
import org.openide.util.Utilities;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Encapsulates the state of a user's CBIR session.
 */
public class CBIRSession {

    public enum Notification {
        NewClassifier,
        DeleteClassifier,
        NewTrainingImages,
        ModelTrained,
        NewQueryPatch
    }

    private static final CBIRSession instance = new CBIRSession();

    private final ProductOrderBasket productOrderBasket;

    private final ProductOrderService productOrderService;

    private final List<Patch> relevantImageList = new ArrayList<>(50);
    private final List<Patch> irrelevantImageList = new ArrayList<>(50);
    private final List<Patch> retrievedImageList = new ArrayList<>(500);
    private final List<Patch> queryPatches = new ArrayList<>(5);

    private final List<Listener> listenerList = new ArrayList<>(1);

    private Classifier classifier;
    private PFAApplicationDescriptor applicationDescriptor;
    private DatabaseManager databaseManager;
    private ClassifierManager classifierManager;

    private String quicklookBandName1;
    private String quicklookBandName2;

    public enum ImageMode {SINGLE, DUAL, FADE}

    private ImageMode imageMode = ImageMode.SINGLE;

    private CBIRSession() {
        productOrderBasket = new ProductOrderBasket();
        productOrderService = new ProductOrderService(productOrderBasket);
    }

    public static CBIRSession getInstance() {
        return instance;
    }

    public boolean hasClassifier() {
        return classifier != null;
    }

    public Classifier getClassifier() {
        return classifier;
    }

    public boolean hasClassifierManager() {
        return classifierManager != null;
    }

    public boolean hasQueryImages() {
        return !queryPatches.isEmpty();
    }

    public String[] listClassifiers() {
        if (hasClassifierManager()) {
            return classifierManager.list();
        } else {
            return new String[0];
        }
    }

    public synchronized DatabaseManager createDatabaseManager(final String uriString) throws URISyntaxException, IOException {
        if (uriString.startsWith("http")) {
            // if HTTP URL: Web Service Client
            URI uri = new URI(uriString);
            if (databaseManager == null || !databaseManager.getURI().equals(uri)) {
                databaseManager = new RestDatabaseManager(uri);
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
            if (databaseManager == null || !databaseManager.getURI().equals(uri)) {
                databaseManager = new LocalDatabaseManager(uri);
            }
        }
        return databaseManager;
    }

    public synchronized void selectDatabase(final String databaseName) throws IOException {
        System.out.println("CBIRSession.selectDatabase");
        System.out.println("databaseName = [" + databaseName + "]");
        classifierManager = databaseManager.createClassifierManager(databaseName);
        System.out.println("classifierManager = " + classifierManager);

        String applicationId = classifierManager.getApplicationId();
        System.out.println("applicationId = " + applicationId);

        applicationDescriptor = PFAApplicationRegistry.getInstance().getDescriptorById(applicationId);
        if(applicationDescriptor == null) {
            throw new IOException("Unknown application id: " + applicationId);
        }
        quicklookBandName1 = applicationDescriptor.getDefaultQuicklookFileName();
        quicklookBandName2 = quicklookBandName1;
    }

    public void createClassifier(String classifierName) throws IOException {
        try {

            classifier = classifierManager.create(classifierName);
            clearPatchLists();

            ContextGlobalExtender contextGlobalExtender = Utilities.actionsGlobalContext().lookup(ContextGlobalExtender.class);
            if (contextGlobalExtender != null) {
                contextGlobalExtender.put("pfa.classifier", classifier);
            }

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

                ContextGlobalExtender contextGlobalExtender = Utilities.actionsGlobalContext().lookup(ContextGlobalExtender.class);
                if (contextGlobalExtender != null) {
                    contextGlobalExtender.put("pfa.classifier", classifier);
                }

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
        queryPatches.clear();
    }

    public void deleteClassifier() throws Exception {
        try {
            Classifier deletedClassifier = classifier;
            if (classifierManager != null) {
                classifierManager.delete(classifier.getName());
            }
            classifier = null;

            ContextGlobalExtender contextGlobalExtender = Utilities.actionsGlobalContext().lookup(ContextGlobalExtender.class);
            if (contextGlobalExtender != null) {
                contextGlobalExtender.remove("pfa.classifier");
            }

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
        return applicationDescriptor;
    }

    public ProductOrderBasket getProductOrderBasket() {
        return productOrderBasket;
    }

    public ProductOrderService getProductOrderService() {
        return productOrderService;
    }

    public FeatureType[] getEffectiveFeatureTypes() {
        FeatureType[] featureTypes = applicationDescriptor.getFeatureTypes();
        Set<String> defaultFeatureSet = applicationDescriptor.getDefaultFeatureSet();
        return AbstractApplicationDescriptor.getEffectiveFeatureTypes(featureTypes, defaultFeatureSet);
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
        queryPatches.add(patch);
        fireNotification(Notification.NewQueryPatch, classifier);
    }

    public Patch[] getQueryPatches() {
        return queryPatches.toArray(new Patch[queryPatches.size()]);
    }

    public void startTraining(final Patch[] queryImages, final ProgressMonitor pm) throws Exception {
        Patch[] ambigousPatches = classifier.startTraining(queryImages, pm);
        getImagesToLabel(ambigousPatches);
    }

    public void trainAndClassify(boolean prePopulate, final ProgressMonitor pm) throws IOException {
        final List<Patch> labeledList = new ArrayList<>(30);
        labeledList.addAll(relevantImageList);
        labeledList.addAll(irrelevantImageList);
        Patch[] labeledPatches = labeledList.toArray(new Patch[labeledList.size()]);

        Patch[] ambigousPatches = classifier.trainAndClassify(prePopulate, labeledPatches, pm);
        retrievedImageList.clear();
        retrievedImageList.addAll(Arrays.asList(ambigousPatches));

        fireNotification(Notification.ModelTrained, classifier);
    }

    public void getMostAmbigousPatches(boolean prePopulate, final ProgressMonitor pm) throws IOException {
        Patch[] mostAmbigous = classifier.getMostAmbigous(prePopulate, pm);
        getImagesToLabel(mostAmbigous);
    }

    private void getImagesToLabel(Patch[] ambigousPatches) throws IOException {
        relevantImageList.clear();
        irrelevantImageList.clear();
        for (Patch patch : ambigousPatches) {
            if (patch.getLabel() == Patch.Label.RELEVANT) {
                relevantImageList.add(patch);
            } else {
                // default to irrelevant so user only needs to select the relevant
                patch.setLabel(Patch.Label.IRRELEVANT);
                irrelevantImageList.add(patch);
            }
        }
        fireNotification(Notification.NewTrainingImages, classifier);
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
     * Not all patches need quicklooks. This function returns a URI to retrive a patch quicklook
     *
     * @param patch             the patches to get quicklooks for
     * @param quicklookBandName the quicklook to retrieve
     * @return patch quicklook URI
     */
    public URI getPatchQuicklookUri(final Patch patch, final String quicklookBandName) throws IOException {
        return classifierManager.getPatchQuicklookUri(patch, quicklookBandName);
    }

    public BufferedImage getPatchQuicklook(final Patch patch, final String quicklookBandName) throws IOException {
        return classifierManager.getPatchQuicklook(patch, quicklookBandName);
    }

    public void loadFeatures(Patch patch) throws IOException {
        if (patch.getFeatures().length == 0) {
            final String featuresAsText = classifierManager.getFeaturesAsText(patch);
            try (Reader reader = new StringReader(featuresAsText)) {
                patch.readFeatures(reader, getEffectiveFeatureTypes());
            }
        }
    }

    public URI getFexOverviewUri(Patch patch) {
        return classifierManager.getFexOverviewUri(patch);
    }

    public File getPatchProductFile(Patch patch) throws IOException {
        return classifierManager.getPatchProductFile(patch);
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
