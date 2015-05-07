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

import org.esa.pfa.fe.op.Patch;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.net.URI;

/**
 * Created by marcoz on 05.05.15.
 */
public interface RestClient {

    Patch[] startTraining(String classifierName, Patch[] queryPatches) throws IOException;

    Patch[] trainAndClassify(String classifierName, boolean prePopulate, Patch[] labeledPatches) throws IOException;

    Patch[] getMostAmbigous(String classifierName, boolean prePopulate) throws IOException;

    URI getPatchQuicklookUri(String classifierName, Patch patch, String quicklookBandName) throws IOException;

    BufferedImage getPatchQuicklook(String classifierName, Patch patch, String quicklookBandName) throws IOException;
}
