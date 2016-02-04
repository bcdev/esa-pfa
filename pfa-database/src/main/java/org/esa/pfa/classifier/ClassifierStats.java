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

/**
 * The statistics of a {@link Classifier}.
 */
public class ClassifierStats {

    private final int numTrainingImages;
    private final int numRetrievedImages;
    private final int numRetrievedImagesMax;
    private final int numRandomImages;
    private final int numIterations;
    private final int numPatchesInTestData;
    private final int numPatchesInQueryData;
    private final int numPatchesInTrainingData;
    private final int numPatchesInDatabase;


    public ClassifierStats(int numTrainingImages,
                           int numRetrievedImages,
                           int numRetrievedImagesMax,
                           int numRandomImages,
                           int numIterations,
                           int numPatchesInTestData,
                           int numPatchesInQueryData,
                           int numPatchesInTrainingData,
                           int numPatchesInDatabase) {
        this.numTrainingImages = numTrainingImages;
        this.numRetrievedImages = numRetrievedImages;
        this.numRetrievedImagesMax = numRetrievedImagesMax;
        this.numRandomImages = numRandomImages;
        this.numIterations = numIterations;
        this.numPatchesInTestData = numPatchesInTestData;
        this.numPatchesInQueryData = numPatchesInQueryData;
        this.numPatchesInTrainingData = numPatchesInTrainingData;
        this.numPatchesInDatabase = numPatchesInDatabase;
    }

    public int getNumTrainingImages() {
        return numTrainingImages;
    }

    public int getNumRetrievedImages() {
        return numRetrievedImages;
    }

    public int getNumRetrievedImagesMax() {
        return numRetrievedImagesMax;
    }

    public int getNumRandomImages() {
        return numRandomImages;
    }

    public int getNumIterations() {
        return numIterations;
    }

    public int getNumPatchesInTestData() {
        return numPatchesInTestData;
    }

    public int getNumPatchesInQueryData() {
        return numPatchesInQueryData;
    }

    public int getNumPatchesInTrainingData() {
        return numPatchesInTrainingData;
    }

    public int getNumPatchesInDatabase() {
        return numPatchesInDatabase;
    }
}
