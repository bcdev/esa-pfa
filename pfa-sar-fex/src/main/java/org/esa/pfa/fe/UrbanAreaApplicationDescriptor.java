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
package org.esa.pfa.fe;

import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.FeatureWriter;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.util.SystemUtils;

import java.awt.Dimension;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class UrbanAreaApplicationDescriptor extends AbstractApplicationDescriptor {

    private static final String NAME = "Urban Area Detection";
    private static final String ID = "UrbanArea";
    private static final String propertyPrefix = "pfa.urbanarea.";
    private static final String DEFAULT_FEATURE_SET =   "speckle_divergence.mean," +
                                                        "speckle_divergence.stdev," +
                                                        "speckle_divergence.cvar," +
                                                        "speckle_divergence.min," +
                                                        "speckle_divergence.max," +
                                                        "speckle_divergence.percentOverPnt4" +
                                                        "speckle_divergence.largestConnectedBlob";
    private static final String DEFAULT_QL_NAME = "sigma0_ql.png";
    private static final String DEFAULT_ALL_QUERY = "product:ASA* OR S1*";  //todo this is a bad default
    private static Dimension patchDimension = new Dimension(200, 200);
    private static Set<String> defaultFeatureSet;
    private static FeatureType[] featureTypes = createFeatureTypes();

    private static Properties properties = new Properties(System.getProperties());

    static {
        File file = new File(SystemUtils.getApplicationDataDir(), propertyPrefix+"properties");
        try {
            try (FileReader reader = new FileReader(file)) {
                properties.load(reader);
            }
        } catch (IOException e) {
            // ok
        }
    }

    public UrbanAreaApplicationDescriptor() {
        super(NAME, ID);
    }

    /**
     * Gets the width and height of the patch segmentation.
     *
     * @return the  dimension
     */
    @Override
    public Dimension getPatchDimension() {
        return patchDimension;
    }

    @Override
    public InputStream getGraphFileAsStream() {
        return UrbanAreaApplicationDescriptor.class.getClassLoader().getResourceAsStream("graphs/UrbanDetectionFeatureWriter.xml");
    }

    @Override
    public String getFeatureWriterPropertyName() {
        // TODO
        return null;
    }

    @Override
    public String getFeatureWriterNodeName() {
        // TODO
        return null;
    }

    @Override
    public URI getDatasetDescriptorURI() {
        try {
            // TODO supply one
            URL url = getClass().getResource("DatasetDescriptor.xml");
            if (url == null) {
                throw new IllegalStateException("DatasetDescriptor.xml missing");
            }
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getAllQueryExpr() {
        return properties.getProperty(propertyPrefix+"allQuery", DEFAULT_ALL_QUERY);
    }

    @Override
    public String getDefaultQuicklookFileName() {
        return properties.getProperty(propertyPrefix+"qlName", DEFAULT_QL_NAME);
    }

    @Override
    public String[] getQuicklookFileNames() {
        return new String[]{"sigma0_ql.png", "speckle_divergence_ql.png"};
    }

    @Override
    public Set<String> getDefaultFeatureSet() {
        if (defaultFeatureSet == null) {
            String property = properties.getProperty(propertyPrefix+"featureSet", DEFAULT_FEATURE_SET);
            defaultFeatureSet = getStringSet(property);
        }
        return defaultFeatureSet;
    }

    @Override
    public FeatureType[] getFeatureTypes() {
        return featureTypes;
    }

    /**
     * @return The product name resolver, or {@code null}.
     * @see ProductNameResolver
     * @see #getDefaultDataAccessPattern
     */
    @Override
    public ProductNameResolver getProductNameResolver() {
        // TODO
        return null;
    }

    /**
     * @return The default data access pattern used to download a data product, or {@code null}.
     * Must be resolvable by the {@link ProductNameResolver product name resolver}.
     */
    @Override
    public String getDefaultDataAccessPattern() {
        // TODO
        return null;
    }

    private static Set<String> getStringSet(String csv) {
        String[] values = csv.split(",");
        for (int i = 0; i < values.length; i++) {
            values[i] = values[i].trim();
        }
        return new HashSet<>(Arrays.asList(values));
    }

    private static FeatureType[] createFeatureTypes() {
        return new FeatureType[]{
                            /*00*/ new FeatureType("patch", "Patch product", Product.class),
                            /*01*/ new FeatureType("sigma0_ql", "Sigma0 quicklook", RenderedImage.class),
                            /*02*/ new FeatureType("speckle_divergence_ql", "Speckle_divergence quicklook", RenderedImage.class),
                            /*03*/ new FeatureType("speckle_divergence", "Speckle divergence statistics", FeatureWriter.STX_ATTRIBUTE_TYPES),
                            /*04*/ new FeatureType("speckle_divergence.percentOverPnt4", "Sample percent over threshold of 0.4", Double.class),
                            /*05*/ new FeatureType("speckle_divergence.largestConnectedBlob", "Largest connected cluster size as a percent of patch", Double.class),
        };
    }
}
