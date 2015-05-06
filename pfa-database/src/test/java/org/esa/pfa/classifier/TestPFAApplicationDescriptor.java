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

import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.op.FeatureType;

import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.Set;

/**
 * For testing only
 */
class TestPFAApplicationDescriptor implements PFAApplicationDescriptor {

    @Override
    public String getName() {
        return "testAppDesc";
    }

    @Override
    public String getId() {
        return "testApp";
    }

    @Override
    public Dimension getPatchDimension() {
        return null;
    }

    @Override
    public InputStream getGraphFileAsStream() {
        return null;
    }

    @Override
    public String getFeatureWriterNodeName() {
        return null;
    }

    @Override
    public String getFeatureWriterPropertyName() {
        return null;
    }

    @Override
    public URI getDatasetDescriptorURI() {
        return null;
    }

    @Override
    public String getAllQueryExpr() {
        return null;
    }

    @Override
    public String getDefaultQuicklookFileName() {
        return null;
    }

    @Override
    public String[] getQuicklookFileNames() {return new String[0];}

    @Override
    public Set<String> getDefaultFeatureSet() {
        return null;
    }

    @Override
    public FeatureType[] getFeatureTypes() {
        return new FeatureType[0];
    }

    @Override
    public File getLocalProductDir() {
        return null;
    }
}
