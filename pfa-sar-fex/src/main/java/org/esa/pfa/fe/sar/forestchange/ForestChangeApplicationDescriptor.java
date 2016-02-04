/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.pfa.fe.sar.forestchange;

import org.esa.pfa.fe.AbstractApplicationDescriptor;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.FeatureWriter;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.util.SystemUtils;

import java.awt.*;
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

public class ForestChangeApplicationDescriptor extends AbstractApplicationDescriptor {

    private static final String NAME = "Forest Change";
    private static final String ID = "Forest Change";
    private static final String propertyPrefix = "pfa.forestchange.";

    private static final String DEFAULT_FEATURE_SET =
            "forestchange.mst_month," +
                    "forestchange.slv_month," +
                    "forestchange.contrast.mean," +
                    "forestchange.contrast.stdev," +
                    "forestchange.contrast.cvar," +
                    "forestchange.contrast.min," +
                    "forestchange.contrast.max," +
                    "forestchange.contrast.count," +
                    "forestchange.homogeneity.mean," +
                    "forestchange.homogeneity.stdev," +
                    "forestchange.homogeneity.cvar," +
                    "forestchange.homogeneity.min," +
                    "forestchange.homogeneity.max," +
                    "forestchange.homogeneity.count," +
                    "forestchange.energy.mean," +
                    "forestchange.energy.stdev," +
                    "forestchange.energy.cvar," +
                    "forestchange.energy.min," +
                    "forestchange.energy.max," +
                    "forestchange.energy.count," +
                    "forestchange.entropy.mean," +
                    "forestchange.entropy.stdev," +
                    "forestchange.entropy.cvar," +
                    "forestchange.entropy.min," +
                    "forestchange.entropy.max," +
                    "forestchange.entropy.count," +
                    "forestchange.variance.mean," +
                    "forestchange.variance.stdev," +
                    "forestchange.variance.cvar," +
                    "forestchange.variance.min," +
                    "forestchange.variance.max," +
                    "forestchange.variance.count,";

    private static final String DEFAULT_QL_NAME = "rgb_ql.png";
    private static final String DEFAULT_ALL_QUERY = "product:ASA* OR S1*"; //todo this is a bad default
    private static Dimension patchDimension = new Dimension(200, 200);
    private static Set<String> defaultFeatureSet;
    private static FeatureType[] featureTypes = createFeatureTypes();

    private static Properties properties = new Properties(System.getProperties());

    static {
        File file = new File(SystemUtils.getApplicationDataDir(), "pfa.forestchange.properties");
        try {
            try (FileReader reader = new FileReader(file)) {
                properties.load(reader);
            }
        } catch (IOException e) {
            // ok
        }
    }

    public ForestChangeApplicationDescriptor() {
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
        return ForestChangeApplicationDescriptor.class.getClassLoader().getResourceAsStream("org/esa/pfa/fe/sar/graphs/ForestChangeQuery.xml");
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

    /**
     * @return The product name resolver, or {@code null}.
     * @see org.esa.pfa.fe.PFAApplicationDescriptor.ProductNameResolver
     * @see #getDefaultDataAccessPattern
     */
    @Override
    public ProductNameResolver getProductNameResolver() {
        // TODO
        return null;
    }

    /**
     * @return The default data access pattern used to download a data product, or {@code null}.
     * Must be resolvable by the {@link org.esa.pfa.fe.PFAApplicationDescriptor.ProductNameResolver product name resolver}.
     */
    @Override
    public String getDefaultDataAccessPattern() {
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
        return properties.getProperty(propertyPrefix + "allQuery", DEFAULT_ALL_QUERY);
    }

    @Override
    public String getDefaultQuicklookFileName() {
        return properties.getProperty(propertyPrefix + "qlName", DEFAULT_QL_NAME);
    }

    @Override
    public String[] getQuicklookFileNames() {
        return new String[]{"rgb_ql.png", "forestchange_ql.png", "mst_ql.png", "slv_ql.png"};
    }

    @Override
    public Set<String> getDefaultFeatureSet() {
        if (defaultFeatureSet == null) {
            String property = properties.getProperty(propertyPrefix + "featureSet", DEFAULT_FEATURE_SET);
            defaultFeatureSet = getStringSet(property);
        }
        return defaultFeatureSet;
    }

    @Override
    public FeatureType[] getFeatureTypes() {
        return featureTypes;
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
                    /*01*/ new FeatureType("rgb_ql", "RGB quicklook", RenderedImage.class),
                    /*02*/ new FeatureType("forestchange_ql", "Forest change mask quicklook", RenderedImage.class),
                    /*03*/ new FeatureType("mst_ql", "Master quicklook", RenderedImage.class),
                    /*04*/ new FeatureType("slv_ql", "Slave quicklook", RenderedImage.class),
                    /*05*/ new FeatureType("forestchange.mst_month", "Master acquisition month", Integer.class),
                    /*06*/ new FeatureType("forestchange.slv_month", "Slave acquisition month", Integer.class),
                    /*07*/ new FeatureType("forestchange.contrast", "Contrast change statistics", FeatureWriter.STX_ATTRIBUTE_TYPES),
                    /*08*/ new FeatureType("forestchange.homogeneity", "Homogeneity change statistics", FeatureWriter.STX_ATTRIBUTE_TYPES),
                    /*09*/ new FeatureType("forestchange.energy", "Energy change statistics", FeatureWriter.STX_ATTRIBUTE_TYPES),
                    /*10*/ new FeatureType("forestchange.entropy", "Entropy change statistics", FeatureWriter.STX_ATTRIBUTE_TYPES),
                    /*11*/ new FeatureType("forestchange.variance", "Variance change statistics", FeatureWriter.STX_ATTRIBUTE_TYPES),
        };
    }
}
