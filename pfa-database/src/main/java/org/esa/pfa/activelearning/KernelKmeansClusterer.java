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
import org.esa.pfa.fe.op.Patch;

import java.util.ArrayList;
import java.util.List;


/**
 * Kernel K-means clustering algorithm.
 * <p>
 * The implementation is based on the following publication:
 * <p>
 * [1] B. Demir, C. Persello, and L. Bruzzone, Batch mode active learning methods for the interactive
 * classification of remote sensing images, IEEE Transactions on Geoscience and Remote Sensing,
 * vol. 49, no.3, pp. 1014-1031, 2011.
 */
public class KernelKmeansClusterer {

    private int maxIterations = 0;
    private int numClusters = 0;
    private int numSamples = 0;
    private List<Patch> samples = new ArrayList<>();
    private Cluster[] clusters = null;
    private SVM svmClassifier = null;
    private boolean debug = false;

    public KernelKmeansClusterer(final int maxIterations, final int numClusters, final SVM svmClassifier) {

        this.maxIterations = maxIterations;
        this.numClusters = numClusters;
        this.clusters = new Cluster[numClusters];
        this.svmClassifier = svmClassifier;
    }

    /**
     * Set samples for clustering.
     *
     * @param uncertainSamples List of m most uncertain samples.
     */
    public void setData(final List<Patch> uncertainSamples) {
        this.numSamples = uncertainSamples.size();
        this.samples = uncertainSamples;
    }

    /**
     * Perform clustering using Kernel K-means clustering algorithm.
     *
     * @param pm a progress monitor
     */
    public void clustering(ProgressMonitor pm) {

        pm.beginTask("Clustering", maxIterations + 1);
        setInitialClusterCenters();
        if (pm.isCanceled()) {
            return;
        }
        pm.worked(1);

        for (int i = 0; i < maxIterations; i++) {

            assignSamplesToClusters();

            if (debug) {
                System.out.println("Iteration: " + i);
                for (int clusterIdx = 0; clusterIdx < clusters.length; clusterIdx++) {
                    System.out.print("Cluster " + clusterIdx + ": ");
                    for (int sampleIdx : clusters[clusterIdx].memberSampleIndices) {
                        System.out.print(sampleIdx + ", ");
                    }
                    System.out.println();
                }
            }

            if (i != maxIterations - 1) {
                updateClusterCenters();

                if (debug) {
                    System.out.println("Updated cluster centers:");
                    for (int clusterIdx = 0; clusterIdx < clusters.length; clusterIdx++) {
                        System.out.println("Cluster " + clusterIdx + ": sample index " + clusters[clusterIdx].centerSampleIdx);
                    }
                }
            }
            if (pm.isCanceled()) {
                return;
            }
            pm.worked(1);
        }
    }

    /**
     * Initialize clusters with each has one randomly selected sample in it.
     */
    private void setInitialClusterCenters() {

        ArrayList<Integer> randomNumbers = new ArrayList<>();
        int k = 0;
        while (k < numClusters) {
            final int idx = (int) (Math.random() * numSamples);
            if (!randomNumbers.contains(idx)) {
                randomNumbers.add(idx);
                clusters[k] = new Cluster();
                clusters[k].memberSampleIndices.add(idx);
                clusters[k].centerSampleIdx = idx;
                k++;
            }
        }

        if (debug) {
            System.out.println("Initial cluster centers:");
            for (int clusterIdx = 0; clusterIdx < clusters.length; clusterIdx++) {
                System.out.println("Cluster " + clusterIdx + ": sample index " + clusters[clusterIdx].centerSampleIdx);
            }
        }
    }

    /**
     * Assign samples to their near clusters base on Euclidean distance in kernel space.
     */
    private void assignSamplesToClusters() {

        for (int sampleIdx = 0; sampleIdx < numSamples; sampleIdx++) {
            if (!isClusterCenter(sampleIdx)) {
                final int clusterIdx = findNearestCluster(sampleIdx);
                clusters[clusterIdx].memberSampleIndices.add(sampleIdx);
            }
        }
    }

    /**
     * Determine if a given sample is the center of any cluster.
     *
     * @param sampleIdx Index of the given sample.
     * @return True if the given sample is the center of some cluster, false otherwise.
     */
    private boolean isClusterCenter(final int sampleIdx) {

        for (int clusterIdx = 0; clusterIdx < numClusters; clusterIdx++) {
            if (sampleIdx == clusters[clusterIdx].centerSampleIdx) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the nearest cluster for each given sample.
     *
     * @param sampleIdx Index of the given sample.
     * @return Cluster index.
     */
    private int findNearestCluster(final int sampleIdx) {

        double minDistance = Double.MAX_VALUE;
        int nearestClusterIdx = 0;
        for (int clusterIdx = 0; clusterIdx < numClusters; clusterIdx++) {
            if (sampleIdx == clusters[clusterIdx].centerSampleIdx) {
                return clusterIdx;
            }

            final double distance = computeDistance(sampleIdx, clusters[clusterIdx].centerSampleIdx);
            if (distance < minDistance) {
                minDistance = distance;
                nearestClusterIdx = clusterIdx;
            }
        }

        return nearestClusterIdx;
    }

    /**
     * Compute kernel space distance between two given samples.
     *
     * @param sampleIdx1 Index of the first sample.
     * @param sampleIdx2 Index of the second sample.
     * @return The kernel space distance.
     */
    private double computeDistance(final int sampleIdx1, final int sampleIdx2) {

        final double[] x1 = samples.get(sampleIdx1).getFeatureValues();
        final double[] x2 = samples.get(sampleIdx2).getFeatureValues();

        return svmClassifier.kernel(x1, x1) - 2 * svmClassifier.kernel(x1, x2) + svmClassifier.kernel(x2, x2);
    }

    /**
     * Update centers of the clusters.
     */
    private void updateClusterCenters() {

        for (int clusterIdx = 0; clusterIdx < numClusters; clusterIdx++) {

            final int sampleIdx = findSampleNearestToClusterCenter(clusterIdx);

            clusters[clusterIdx].centerSampleIdx = sampleIdx;
            clusters[clusterIdx].memberSampleIndices.clear();
            clusters[clusterIdx].memberSampleIndices.add(sampleIdx);
        }
    }

    /**
     * Find the sample that is nearest to the center of a given cluster.
     *
     * @param clusterIdx The cluster index.
     * @return The sample index.
     */
    private int findSampleNearestToClusterCenter(final int clusterIdx) {

        final double sum2 = computeSum2(clusters[clusterIdx].memberSampleIndices);

        int sampleIdx = 0;
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < clusters[clusterIdx].memberSampleIndices.size(); i++) {

            final double distance = computeDistanceToClusterCenter(clusters[clusterIdx].memberSampleIndices.get(i),
                                                                   clusters[clusterIdx].memberSampleIndices, sum2);

            if (distance < minDistance) {
                minDistance = distance;
                sampleIdx = clusters[clusterIdx].memberSampleIndices.get(i);
            }
        }

        return sampleIdx;
    }

    /**
     * For a cluster with n samples {xi, i=1,...,n}, this function computes the summation of K(xi,xj), i,j=1,...,n.
     * This summation will be used in the calculation of distance between a given sample and a given cluster.
     *
     * @param memberSampleIndices The list of indices of samples in a given cluster.
     * @return The summation.
     */
    private double computeSum2(final List<Integer> memberSampleIndices) {

        double sum2 = 0.0;
        for (Integer memberSampleIndice : memberSampleIndices) {
            final double[] xi = samples.get(memberSampleIndice).getFeatureValues();
            for (Integer memberSampleIndice1 : memberSampleIndices) {
                final double[] xj = samples.get(memberSampleIndice1).getFeatureValues();
                sum2 += svmClassifier.kernel(xi, xj);
            }
        }
        return sum2;
    }

    /**
     * Compute the distance between a given sample and a given cluster.
     *
     * @param sampleIdx           The index of the given sample.
     * @param memberSampleIndices The list of indices of samples in the given cluster.
     * @param sum2                The summation computed by computeSum2 function.
     * @return The distance.
     */
    private double computeDistanceToClusterCenter(
            final int sampleIdx, final List<Integer> memberSampleIndices, final double sum2) {

        final int numSamples = memberSampleIndices.size();
        final double[] x = samples.get(sampleIdx).getFeatureValues();

        double sum1 = 0.0;
        for (Integer idx : memberSampleIndices) {
            final double[] xi = samples.get(idx).getFeatureValues();
            sum1 += svmClassifier.kernel(x, xi);
        }

        return svmClassifier.kernel(x, x) - 2.0 * sum1 / numSamples + sum2 / (numSamples * numSamples);
    }

    /**
     * Get representatives of the clusters using density criterion.
     *
     * @return patchIDs Array of IDs of the selected patches.
     */
    public int[] getRepresentatives() {

        int[] rep = new int[numClusters];
        int[] patchIDs = new int[numClusters];
        for (int i = 0; i < numClusters; i++) {
            int sampleIdx = findHighestDensitySample(clusters[i]);
            patchIDs[i] = samples.get(sampleIdx).getID();
            rep[i] = sampleIdx;
        }

        if (debug) {
            for (int clusterIdx = 0; clusterIdx < clusters.length; clusterIdx++) {
                System.out.println("Cluster " + clusterIdx + ": representative sample index " + rep[clusterIdx]);
            }
        }

        return patchIDs;
    }

    /**
     * Find the representative sample in a given cluster based on density criterion.
     *
     * @param cluster The given cluster.
     * @return The representative sample index.
     */
    private int findHighestDensitySample(Cluster cluster) {

        // The density of a sample in a cluster is defined through the average distance of the sample to all other
        // samples in the cluster. Therefore, the lower the average distance, the higher the density.
        double leastAverageDistance = Double.MAX_VALUE;
        int sampleIdx = 0;
        for (Integer idx : cluster.memberSampleIndices) {
            final double averageDistance = computeAverageDistance(idx, cluster.memberSampleIndices);
            if (averageDistance < leastAverageDistance) {
                leastAverageDistance = averageDistance;
                sampleIdx = idx;
            }
        }

        return sampleIdx;
    }

    /**
     * Compute the average distance of a given sample to all other samples in its cluster.
     *
     * @param sampleIdx           The index of the given sample.
     * @param memberSampleIndices The list of indices of samples in the cluster.
     * @return The average distance.
     */
    private double computeAverageDistance(final int sampleIdx, final List<Integer> memberSampleIndices) {

        final int numSamples = memberSampleIndices.size();
        final double[] x = samples.get(sampleIdx).getFeatureValues();

        double sum1 = 0.0, sum2 = 0.0;
        for (Integer idx : memberSampleIndices) {
            final double[] xi = samples.get(idx).getFeatureValues();
            sum1 += svmClassifier.kernel(x, xi);
            sum2 += svmClassifier.kernel(xi, xi);
        }

        return svmClassifier.kernel(x, x) - 2.0 * sum1 / numSamples + sum2 / numSamples;
    }

    /**
     * An individual cluster.
     */
    public static class Cluster {
        public List<Integer> memberSampleIndices = new ArrayList<>();
        public int centerSampleIdx;
    }
}
