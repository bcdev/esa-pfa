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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * CRUD interface for the Classifier
 */
public interface ClassifierManager {

    String getApplicationId();

    String getDatabaseName();

    String[] list();

    Classifier create(String classifierName) throws IOException;

    void delete(String classifierName) throws IOException;

    Classifier get(String classifierName) throws IOException;

    URI getPatchQuicklookUri(Patch patch, String quicklookBandName) throws IOException;

    BufferedImage getPatchQuicklook(Patch patch, String quicklookBandName) throws IOException;

    File getPatchProductFile(Patch patch) throws IOException;

    String getFeaturesAsText(Patch patch) throws IOException;

    URI getFexOverviewUri(Patch patch);

}
