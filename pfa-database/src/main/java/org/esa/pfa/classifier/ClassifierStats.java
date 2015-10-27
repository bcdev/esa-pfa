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
 * @author marcoz
 */
public class ClassifierStats {

    private final int numTrainingImages;
    private final int numRetrievedImages;
    private final int numIterationss;
    private final int numPatchesInTestData;
    private final int numNumPatchesInQueryData;
    private final int numNumPatchesInTrainingData;

    public ClassifierStats(int numTrainingImages, int numRetrievedImages, int numIterationss, int numPatchesInTestData, int numNumPatchesInQueryData, int numNumPatchesInTrainingData) {
        this.numTrainingImages = numTrainingImages;
        this.numRetrievedImages = numRetrievedImages;
        this.numIterationss = numIterationss;
        this.numPatchesInTestData = numPatchesInTestData;
        this.numNumPatchesInQueryData = numNumPatchesInQueryData;
        this.numNumPatchesInTrainingData = numNumPatchesInTrainingData;
    }

    public int getNumTrainingImages() {
        return numTrainingImages;
    }

    public int getNumRetrievedImages() {
        return numRetrievedImages;
    }

    public int getNumIterations() {
        return numIterationss;
    }

    public int getNumPatchesInTestData() {
        return numPatchesInTestData;
    }

    public int getNumPatchesInQueryData() {
        return numNumPatchesInQueryData;
    }

    public int getNumPatchesInTrainingData() {
        return numNumPatchesInTrainingData;
    }

}
