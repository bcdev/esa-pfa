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

import org.esa.pfa.fe.op.Patch;

import java.io.IOException;

/**
 * Created by marcoz on 17.04.15.
 */
public interface ClassifierService {

    String[] list();

    Classifier create(String classifierName, String applicationName) throws IOException;

    void delete(String classifierName) throws IOException;

    Classifier get(String classifierName) throws IOException;

    void save(Classifier classifier) throws IOException;

    void startTraining(Patch[] qeueryPatches);

    Patch[] getmostAmbigous(int numTrainingImages);

    void train(Patch[] labeledPatches);

    Patch[] getBestPatches();
}
