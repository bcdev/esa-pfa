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

package org.esa.pfa.ws;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.pfa.classifier.RealClassifier;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;

import java.io.File;
import java.io.IOException;

/**
 * Created by marcoz on 24.04.15.
 */
public class RealWebClassifier implements RealClassifier {


    @Override
    public int getNumTrainingImages() {
        return 0;
    }

    @Override
    public void setNumTrainingImages(int numTrainingImages) {

    }

    @Override
    public int getNumRetrievedImages() {
        return 0;
    }

    @Override
    public void setNumRetrievedImages(int numRetrievedImages) {

    }

    @Override
    public void saveClassifier() throws IOException {

    }

    @Override
    public void startTraining(Patch[] queryPatches, ProgressMonitor pm) throws IOException {

    }

    @Override
    public Patch[] getMostAmbigousPatches(ProgressMonitor pm) {
        return new Patch[0];
    }

    @Override
    public void train(Patch[] labeledPatches, ProgressMonitor pm) throws IOException {

    }

    @Override
    public Patch[] classify() {
        return new Patch[0];
    }

    @Override
    public int getNumIterations() {
        return 0;
    }

    @Override
    public FeatureType[] getEffectiveFeatureTypes() {
        return new FeatureType[0];
    }

    @Override
    public String[] getAvailableQuickLooks(Patch patch) throws IOException {
        return new String[0];
    }

    @Override
    public void populateArchivePatches(ProgressMonitor pm) {

    }

    @Override
    public void getPatchQuicklook(Patch patch, String quicklookBandName) {

    }

    @Override
    public File getPatchProductFile(Patch patch) throws IOException {
        return null;
    }
}
