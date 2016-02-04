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
package org.esa.pfa.activelearning;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import org.esa.pfa.classifier.ClassifierModel;
import org.esa.pfa.fe.op.Patch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * The active learning 'engine'.
 * <p>
 * The implementation is based on the following publication:
 * <p>
 * [1] Begum Demir and Lorenzo Bruzzone, "An effective active learning method for interactive content-based retrieval
 * in remote sensing images", Geoscience and Remote Sensing Symposium (IGARSS), 2013 IEEE International.
 */
public class ActiveLearning {

    private static final int FMIN = 0;
    private static final int FMAX = 1;
    private static final int NUM_INITIAL_ITERATIONS = 3; // AL parameter
    private static final int MAX_ITERATIONS_KMEANS = 10; // KKC parameter
    private static final int NUM_FOLDS = 5;    // SVM parameter: number of folds for cross validation
    private static final double LOWER_LIMIT = 0.0;  // SVM parameter: training data scaling lower limit
    private static final double UPPER_LIMIT = 1.0;  // SVM parameter: training data scaling upper limit

    private static final boolean DEBUG = false;

    private final ClassifierModel model;
    private final SVM svmClassifier;

    public ActiveLearning(ClassifierModel model) {
        this.model = model;
        svmClassifier = new SVM(NUM_FOLDS, LOWER_LIMIT, UPPER_LIMIT, model.getSvmModelReference());
    }

    /**
     * Set training data with relevant patches from query image.
     *
     * @param queryPatches The patch array.
     */
    public void setQueryPatches(final Patch[] queryPatches) {

        model.getTrainingData().clear();
        checkQueryPatchesValidation(queryPatches);
        model.getTrainingData().addAll(Arrays.asList(queryPatches));

        model.getQueryData().clear();
        model.getQueryData().addAll(Arrays.asList(queryPatches));

        if (DEBUG) {
            System.out.println("Number of patches from query image: " + queryPatches.length);
        }
    }

    /**
     * Set random patches obtained from archive. Some patches are added to training set as irrelevant patches.
     * The rest will be used in active learning.
     *
     * @param patchArray The patch array.
     */
    public void setRandomPatches(final Patch[] patchArray, final ProgressMonitor pm) {

        setTestDataSetWithValidPatches(patchArray);

        if (model.getNumIterations() == 0) {
            setInitialTrainingSet();
            svmClassifier.train(model.getTrainingData(), pm);
        }

        if (DEBUG) {
            System.out.println("Number of random patches: " + patchArray.length);
            System.out.println("Number of patches in test data pool: " + model.getTestData().size());
            System.out.println("Number of patches in training data set: " + model.getTrainingData().size());
        }
    }

    /**
     * Set training data set with training patches saved.
     */
    public void setTrainingData(ProgressMonitor pm) {
        List<Patch> trainingData = model.getTrainingData();
        if (trainingData.size() > 0) {
            svmClassifier.train(trainingData, pm);
        }
    }

    /**
     * Get the most ambiguous patches selected by the active learning algorithm.
     *
     * @param numDiversePatches The number of ambiguous patches.
     * @param pm A progress monitor
     * @return The patch array.
     */
    public Patch[] getMostAmbiguousPatches(final int numDiversePatches, final ProgressMonitor pm) {

        final int numUncertainPatches = 4 * numDiversePatches;
        if (DEBUG) {
            System.out.println("Number of uncertain patches to select: " + numUncertainPatches);
            System.out.println("Number of diverse patches to select: " + numDiversePatches);
        }

        List<Patch> diverseSamples = Collections.emptyList();
        pm.beginTask("Get Most Ambiguous Patches", 100);
        try {

            List<Patch> uncertainSamples = selectMostUncertainSamples(numUncertainPatches);
            pm.worked(10);
            if (pm.isCanceled()) {
                return new Patch[0];
            }

            diverseSamples = selectMostDiverseSamples(numDiversePatches, uncertainSamples, SubProgressMonitor.create(pm, 90));
            if (pm.isCanceled()) {
                return new Patch[0];
            }
        } finally {
            pm.done();
        }

        if (DEBUG) {
            for (Patch patch : diverseSamples) {
                System.out.println("Ambiguous patch: x" + patch.getPatchX() + "y" + patch.getPatchY());
            }
        }

        return diverseSamples.toArray(new Patch[diverseSamples.size()]);
    }

    /**
     * Update training set with user labeled patches and train the classifier.
     *
     * @param userLabelledPatches The user labeled patch array.
     */
    public void train(final Patch[] userLabelledPatches, final ProgressMonitor pm) {

        checkLabels(userLabelledPatches);

        model.getTrainingData().addAll(Arrays.asList(userLabelledPatches));

        svmClassifier.train(model.getTrainingData(), pm);

        model.setNumIterations(model.getNumIterations() + 1);

        if (DEBUG) {
            System.out.println("Number of patches in training data set: " + model.getTrainingData().size());
        }
    }

    /**
     * Classify an array of patches. UI needs to sort the patches according to their distances to hyperplane.
     *
     * @param patchArray The Given patch array.
     */
    public void classify(final Patch[] patchArray) {

        final double[] decValues = new double[1];
        for (Patch patch : patchArray) {
            double p = svmClassifier.classify(patch.getFeatureValues(), decValues);
            final Patch.Label label = p < 1 ? Patch.Label.IRRELEVANT : Patch.Label.RELEVANT;
            patch.setLabel(label);
            patch.setDistance(Math.abs(decValues[0]));
            //System.out.println("Classified patch: x" + patch.getPatchX() + "y" + patch.getPatchY() + ", label: " + label);
        }

        if (DEBUG) {
            System.out.println("Number of patches to classify: " + patchArray.length);
        }
    }

    /**
     * Check validity of the query patches.
     *
     * @param patchArray The patch array.
     */
    private static void checkQueryPatchesValidation(final Patch[] patchArray) {

        EnumSet<Patch.Label> classLabels =  EnumSet.noneOf(Patch.Label.class);
        for (Patch patch : patchArray) {
            classLabels.add(patch.getLabel());
            if (!checkFeatureValidation(patch.getFeatureValues())) {
                throw new IllegalArgumentException("Found invalid feature value in query patch.");
            }
        }

        if (classLabels.size() > 1) {
            throw new IllegalArgumentException("Found different labels in query patches.");
        }
    }

    /**
     * Check validation of given features.
     *
     * @param featureValues The feature value array.
     * @return True if all features are valid, false otherwise.
     */
    private static boolean checkFeatureValidation(final double[] featureValues) {
        for (double v : featureValues) {
            if (Double.isNaN(v)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Set test data set with valid random patches.
     *
     * @param patchArray The patch array.
     */
    private void setTestDataSetWithValidPatches(final Patch[] patchArray) {

        int counter = 0;
        for (Patch patch : patchArray) {
            if (checkFeatureValidation(patch.getFeatureValues())) {
                model.getTestData().add(patch);
                counter++;
            }
        }

        if (DEBUG) {
            System.out.println("Number of invalid random patches: " + (patchArray.length - counter));
        }
    }

    /**
     * Set initial training data set with relevant patches from query image and irrelevant patches from random patches.
     * Random patches that are not close to the query patches are considered irrelevant. Euclidean space distance
     * is used in measuring the distance between patches.
     */
    private void setInitialTrainingSet() {

        final double[][] featureMinMax = getFeatureMinMax();

        final double[] relevantPatchClusterCenter = computeClusterCenter(model.getTrainingData());

        final double[][] distance = computeDistanceToClusterCenter(relevantPatchClusterCenter, featureMinMax);

        java.util.Arrays.sort(distance, (a, b) -> Double.compare(b[1], a[1]));

        final int numIrrelevantSample = Math.min(model.getQueryData().size(), distance.length);
        int[] patchIDs = new int[numIrrelevantSample];
        for (int i = 0; i < numIrrelevantSample; i++) {
            final Patch patch = model.getTestData().get((int) distance[i][0]);
            patch.setLabel(Patch.Label.IRRELEVANT);
            patchIDs[i] = patch.getID();
            model.getTrainingData().add(patch);
        }

        for (Iterator<Patch> itr = model.getTestData().iterator(); itr.hasNext(); ) {
            Patch patch = itr.next();
            for (int patchID : patchIDs) {
                if (patch.getID() == patchID) {
                    itr.remove();
                    break;
                }
            }
        }
    }

    /**
     * Get lower and upper bounds for all features.
     */
    private double[][] getFeatureMinMax() {

        List<Patch> tempList = new ArrayList<>();
        tempList.addAll(model.getTestData());
        tempList.addAll(model.getQueryData());

        int numFeatures = tempList.get(0).getFeatureValues().length;
        double[][] featureMinMax = new double[2][numFeatures];
        Arrays.fill(featureMinMax[FMIN], Double.MAX_VALUE);
        Arrays.fill(featureMinMax[FMAX], -Double.MAX_VALUE);

        for (Patch patch : tempList) {
            final double[] featureValues = patch.getFeatureValues();
            for (int i = 0; i < featureValues.length; i++) {
                if (featureValues[i] < featureMinMax[FMIN][i]) {
                    featureMinMax[FMIN][i] = featureValues[i];
                }

                if (featureValues[i] > featureMinMax[FMAX][i]) {
                    featureMinMax[FMAX][i] = featureValues[i];
                }
            }
        }
        return featureMinMax;
    }

    /**
     * Normalize all features to range [0,1].
     *
     * @param features The feature array.
     * @return Normalized features.
     */
    private double[] scale(final double[] features, final double[][] featureMinMax) {
        double[] scaledFeatures = new double[features.length];
        for (int i = 0; i < features.length; i++) {
            scaledFeatures[i] = scale(i, features[i], featureMinMax);
        }
        return scaledFeatures;
    }

    /**
     * Normalize a given feature to range [0,1].
     *
     * @param featureIdx   The feature index.
     * @param featureValue The feature value.
     * @return The normalized feature.
     */
    private double scale(final int featureIdx, final double featureValue, final double[][] featureMinMax) {
        if (featureMinMax[FMIN][featureIdx] < featureMinMax[FMAX][featureIdx]) {
            double lambda = (featureValue - featureMinMax[FMIN][featureIdx]) / (featureMinMax[FMAX][featureIdx] - featureMinMax[FMIN][featureIdx]);
            return LOWER_LIMIT + lambda * (UPPER_LIMIT - LOWER_LIMIT);
        } else {
            return LOWER_LIMIT;
        }
    }

    /**
     * Compute cluster center of the given list of patches.
     *
     * @param patchList The patch list.
     * @return The cluster center.
     */
    private static double[] computeClusterCenter(final List<Patch> patchList) {
        double[] center = new double[patchList.get(0).getFeatureValues().length];
        for (Patch patch : patchList) {
            final double[] featureValues = patch.getFeatureValues();
            for (int i = 0; i < featureValues.length; i++) {
                center[i] += featureValues[i];
            }
        }

        for (int i = 0; i < center.length; i++) {
            center[i] /= patchList.size();
        }

        return center;
    }

    /**
     * Compute for all samples the Euclidean distance to the center of the cluster.
     *
     * @param clusterCenter The cluster center.
     * @return The distance array.
     */
    private double[][] computeDistanceToClusterCenter(final double[] clusterCenter, final double[][] featureMinMax) {

        final double[][] distance = new double[model.getTestData().size()][2];
        int k = 0;
        for (Patch patch : model.getTestData()) {
            distance[k][0] = k; // sample index in testData
            double[] x1 = scale(patch.getFeatureValues(), featureMinMax);
            double[] x2 = scale(clusterCenter, featureMinMax);
            distance[k][1] = computeEuclideanDistance(x1, x2);
            k++;
        }

        return distance;
    }

    /**
     * Compute Euclidean space distance between two given points.
     *
     * @param x1 The first point.
     * @param x2 The second point.
     * @return The distance.
     */
    private static double computeEuclideanDistance(final double[] x1, final double[] x2) {
        double distance = 0.0;
        for (int i = 0; i < x1.length; i++) {
            distance += (x1[i] - x2[i]) * (x1[i] - x2[i]);
        }
        return distance;
    }

    /**
     * Check if there is any unlabeled patch.
     *
     * @param patchArray Patch array.
     */
    private static void checkLabels(final Patch[] patchArray) {
        for (Patch patch : patchArray) {
            if (patch.getLabel() == Patch.Label.NONE) {
                throw new IllegalArgumentException("Found unlabeled patch(s)");
            }
        }
    }

    /**
     * Select uncertain samples from test data.
     *
     * @param numUncertainPatches Number of uncertainty samples selected with uncertainty criterion
     */
    private List<Patch> selectMostUncertainSamples(int numUncertainPatches) {

        final PatchDistance[] distance = computeFunctionalDistanceForAllSamples();

        List<Patch> uncertainSamples;
        if (model.getNumIterations() < NUM_INITIAL_ITERATIONS) {
            uncertainSamples = getAllUncertainSamples(distance);
            if (uncertainSamples.size() < numUncertainPatches) {
                uncertainSamples = getMostUncertainSamples(numUncertainPatches, distance);
            }
        } else {
            uncertainSamples = getMostUncertainSamples(numUncertainPatches, distance);
        }

        if (DEBUG) {
            System.out.println("Number of uncertain patches selected: " + uncertainSamples.size());
        }
        return uncertainSamples;
    }

    /**
     * Compute functional distance for all samples in test data set.
     *
     * @return The distance array.
     */
    private PatchDistance[] computeFunctionalDistanceForAllSamples() {
        final PatchDistance[] distance = new PatchDistance[model.getTestData().size()];
        for (int i = 0; i < model.getTestData().size(); i++) {
            distance[i] = new PatchDistance(i, computeFunctionalDistance(model.getTestData().get(i).getFeatureValues()));
        }
        return distance;
    }

    /**
     * Compute functional distance of a given sample to the SVM hyperplane.
     *
     * @return The functional distance.
     * @param featureValues the values fo the features
     */
    private double computeFunctionalDistance(double[] featureValues) {
        final double[] decValues = new double[1];
        svmClassifier.classify(featureValues, decValues);
        return Math.abs(decValues[0]);
    }

    /**
     * Get all uncertain samples from test data set if their functional distances are less than 1.
     *
     * @param distance The functional distance array.
     */
    private List<Patch> getAllUncertainSamples(PatchDistance[] distance) {
        List<Patch> uncertainSamples = new ArrayList<>();
        for (PatchDistance aDistance : distance) {
            if (aDistance.distance < 1.0) {
                uncertainSamples.add(model.getTestData().get(aDistance.index));
            }
        }
        return uncertainSamples;
    }

    /**
     * Get q most uncertain samples from test data set based on their functional distances.
     *
     * @param numUncertainPatches Number of uncertainty samples selected with uncertainty criterion
     * @param distance            The functional distance array.
     */
    private List<Patch> getMostUncertainSamples(int numUncertainPatches, PatchDistance[] distance) {

        java.util.Arrays.sort(distance, (a, b) -> Double.compare(a.distance, b.distance));

        List<Patch> uncertainSamples = new ArrayList<>();
        final int maxUncertainSample = Math.min(numUncertainPatches, distance.length);
        for (int i = 0; i < maxUncertainSample; i++) {
            uncertainSamples.add(model.getTestData().get(distance[i].index));
        }
        return uncertainSamples;
    }

    /**
     * Select h most diverse samples from the q most uncertain samples.
     *
     * @param numDiversePatches Number of batch samples selected with diversity and density criteria
     * @param pm A progress monitor
     */
    private List<Patch> selectMostDiverseSamples(int numDiversePatches, List<Patch> uncertainSamples, ProgressMonitor pm) {

        KernelKmeansClusterer kkc = new KernelKmeansClusterer(MAX_ITERATIONS_KMEANS, numDiversePatches, svmClassifier);
        kkc.setData(uncertainSamples);
        kkc.clustering(pm);
        if (pm.isCanceled()) {
            return Collections.emptyList();
        }
        final int[] diverseSampleIDs = kkc.getRepresentatives();

        List<Patch> diverseSamples = new ArrayList<>();
        for (int patchID : diverseSampleIDs) {
            for (Iterator<Patch> itr = model.getTestData().iterator(); itr.hasNext(); ) {
                Patch patch = itr.next();
                if (patch.getID() == patchID) {
                    diverseSamples.add(patch);
                    itr.remove();
                    break;
                }
            }
        }

        if (DEBUG) {
            System.out.println("Number of diverse patches IDs: " + diverseSampleIDs.length);
            System.out.println("Number of diverse patches selected: " + diverseSamples.size());
        }

        if (diverseSamples.size() != diverseSampleIDs.length) {
            throw new IllegalArgumentException("Invalid diverse patch array.");
        }
        return diverseSamples;
    }


    private static class PatchDistance {
        final int index;
        final double distance;

        public PatchDistance(int index, double distance) {
            this.index = index;
            this.distance = distance;
        }
    }
}