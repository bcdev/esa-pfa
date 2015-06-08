/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
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
package org.esa.pfa.fe;

import org.esa.pfa.fe.op.FeatureType;

import java.awt.Dimension;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.Set;

/**
 * Describe the feature extraction application.
 *
 * @author Luis Veci
 * @author Norman Fomferra
 */
public interface PFAApplicationDescriptor {

    /**
     * The name
     *
     * @return a name
     */
    String getName();

    /**
     * The (unique) ID
     *
     * @return the ID
     */
    String getId();

    /**
     * Gets the width and height of the patch segmentation.
     *
     * @return the  dimension
     */
    Dimension getPatchDimension();

    /**
     * Gets the graph file with which to apply the feature extraction
     *
     * @return the graph file as a stream
     * TODO return a URL / URI instead
     */
    public InputStream getGraphFileAsStream();

    /**
     * The name of the node in the graph that conatins the {@link org.esa.pfa.fe.op.FeatureWriter}
     *
     * @return the name of the node
     */
    public String getFeatureWriterNodeName();

    /**
     * The name of the target property in the {@link org.esa.pfa.fe.op.FeatureWriter} operator
     * that contains the {@link org.esa.pfa.fe.op.FeatureWriterResult}.
     *
     * @return the name of the target property
     */
    public String getFeatureWriterPropertyName();

    /**
     * Gets the dataset descriptor (Schema) for the index of this application.
     *
     * @return the dataset descriptor
     */
    public URI getDatasetDescriptorURI();

    /**
     * @return A Lucene query expression that matches all entries.
     */
    String getAllQueryExpr();

    /**
     * @return The name of the default quicklook file to be used.
     * May be {@code null}, and if so, an arbitrary quicklook file will be used.
     */
    String getDefaultQuicklookFileName();

    /**
     * @return The names of all quicklook file names produced.
     */
    String[] getQuicklookFileNames();

    /**
     * @return The name of the numeric features to be used by the classifier. May be {@code null}, and if so, all numeric features will be used.
     */
    Set<String> getDefaultFeatureSet();

    /**
     * @return The features types provided
     */
    FeatureType[] getFeatureTypes();

    /**
     * @return The product name resolver, or {@code null}.
     * @see ProductNameResolver
     * @see #getDefaultDataAccessPattern
     */
    ProductNameResolver getProductNameResolver();

    /**
     * @return The default data access pattern used to download a data product, or {@code null}.
     * Must be resolvable by the {@link ProductNameResolver product name resolver}.
     */
    String getDefaultDataAccessPattern();

    /**
     * Resolves product pattern strings given a product name (ID).
     */
    interface ProductNameResolver {
        /**
         * Must be capable of resolving the following:
         * <ol>
         *     <li>${name}</li> - Product name
         *     <li>${yyyy}</li> - Sensing year, 4 digits
         *     <li>${MM}</li> - Sensing month of year, 2 digits
         *     <li>${dd}</li> - Sensing day of month, 2 digits
         * </ol>
         * @param pattern A pattern, e.g. "http://www.brockmann-consult.de/pfa/data-access/MER_RR__1P/r03/${yyyy}/${MM}/${dd}/${name}"
         * @param productName A product name, e.g. "MER_RR__1PPEPA20080505_035110_000000592068_00190_32308_7749.N1"
         * @return The resolved pattern
         */
        String resolve(String pattern, String productName);
    }
}
