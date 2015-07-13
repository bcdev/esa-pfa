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
package org.esa.pfa.fe.spectral;

import org.esa.pfa.fe.AbstractApplicationDescriptor;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.FeatureWriter;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.util.Debug;
import org.esa.snap.util.SystemUtils;

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

public class BiTempSpectralApplicationDescriptor extends AbstractApplicationDescriptor {

    private static final String NAME = "Algal Bloom Detection";
    private static final String ID = "AlgalBloom";
    public static final String DEFAULT_FEATURE_SET = "flh.mean,mci.mean,flh_hg_pixels";
    public static final String DEFAULT_QL_NAME = "rgb1_ql.png";
    public static final String DEFAULT_ALL_QUERY = "product:MER*";

    public static final String R_EXPR = "log(0.05 + 0.35 * reflec_2 + 0.60 * reflec_5 + reflec_6 + 0.13 * reflec_7)";
    public static final String G_EXPR = "log(0.05 + 0.21 * reflec_3 + 0.50 * reflec_4 + reflec_5 + 0.38 * reflec_6)";
    public static final String B_EXPR = "log(0.05 + 0.21 * reflec_1 + 1.75 * reflec_2 + 0.47 * reflec_3 + 0.16 * reflec_4)";


    private static Properties properties = new Properties(System.getProperties());

    private static Dimension patchDimension = new Dimension(200, 200);
    private static Set<String> defaultFeatureSet;
    private static File localProductDir;
    private static FeatureType[] featureTypes = createFeatureTypes();


    static {
        //TODO this doesn't work on calvalus
        File file = new File(SystemUtils.getApplicationDataDir(), "pfa-algalblooms.properties");
        try {
            try (FileReader reader = new FileReader(file)) {
                properties.load(reader);
            }
        } catch (IOException e) {
            Debug.trace(e);
            // ok
        }
    }

    public BiTempSpectralApplicationDescriptor() {
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
        return getClass().getResourceAsStream("AlgalBloomFeatureWriter.xml");
    }

    @Override
    public String getFeatureWriterNodeName() {
        return "AlgalBloomFeatureWriter";
    }

    @Override
    public String getFeatureWriterPropertyName() {
        return "result";
    }

    @Override
    public URI getDatasetDescriptorURI() {
        try {
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
        return properties.getProperty("pfa.algalblooms.allQuery", DEFAULT_ALL_QUERY);
    }

    @Override
    public String getDefaultQuicklookFileName() {
        return properties.getProperty("pfa.algalblooms.qlName", DEFAULT_QL_NAME);
    }

    @Override
    public String[] getQuicklookFileNames() {
        return new String[]{"rgb1_ql.png", "rgb2_ql.png", "flh_ql.png", "mci_ql.png", "chl_ql.png"};
    }

    @Override
    public ProductNameResolver getProductNameResolver() {
        return (pattern, productName) -> {
            // MER_RR__1PRACR20080713_034425_000026382070_00176_33296_0000.N1
            // 0123456789012345678901
            String yyyy = productName.substring(14, 14 + 4);
            String MM = productName.substring(14 + 4, 14 + 6);
            String dd = productName.substring(14 + 6, 14 + 8);
            String name = productName;
            return pattern
                    .replace("${yyyy}", yyyy)
                    .replace("${MM}", MM)
                    .replace("${dd}", dd)
                    .replace("${name}", name);
        };
    }

    @Override
    public String getDefaultDataAccessPattern() {
        return "http://pfa:wWaNP58o@www.brockmann-consult.de/glass/pfa/MER_RR__1P/r03/${yyyy}/${MM}/${dd}/${name}";
    }

    @Override
    public Set<String> getDefaultFeatureSet() {
        if (defaultFeatureSet == null) {
            String property = properties.getProperty("pfa.algalblooms.featureSet", DEFAULT_FEATURE_SET);
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
                                /*00*/
                new FeatureType("patch", "Patch product", Product.class),
                                /*01*/
                new FeatureType("rgb1_ql", "RGB quicklook for TOA reflectances (fixed range)", RenderedImage.class),
                                /*02*/
                new FeatureType("rgb2_ql", "RGB quicklook for TOA reflectances (dynamic range, ROI only)",
                                RenderedImage.class),
                                /*03*/
                new FeatureType("flh_ql",
                                "Grey-scale quicklook for 'flh'",
                                RenderedImage.class),
                                /*04*/
                new FeatureType("mci_ql",
                                "Grey-scale quicklook for 'mci'",
                                RenderedImage.class),
                                /*05*/
                new FeatureType("chl_ql",
                                "Grey-scale quicklook for 'chl'",
                                RenderedImage.class),
                                /*06*/
                new FeatureType("flh", "Fluorescence Line Height", FeatureWriter.STX_ATTRIBUTE_TYPES),
                                /*07*/
                new FeatureType("mci", "Maximum Chlorophyll Index", FeatureWriter.STX_ATTRIBUTE_TYPES),
                                /*08*/
                new FeatureType("chl", "Chlorophyll Concentration", FeatureWriter.STX_ATTRIBUTE_TYPES),
                                /*09*/
                new FeatureType("red", "Red channel (" + R_EXPR + ")", FeatureWriter.STX_ATTRIBUTE_TYPES),
                                /*10*/
                new FeatureType("green", "Green channel (" + G_EXPR + ")", FeatureWriter.STX_ATTRIBUTE_TYPES),
                                /*11*/
                new FeatureType("blue", "Blue channel (" + B_EXPR + ")", FeatureWriter.STX_ATTRIBUTE_TYPES),
                                /*12*/
                new FeatureType("coast_dist", "Distance from next coast pixel (km)", FeatureWriter.STX_ATTRIBUTE_TYPES),
                                /*13*/
                new FeatureType("flh_hg_pixels", "FLH high-gradient pixel ratio", Double.class),
                                /*14*/
                new FeatureType("valid_pixels", "Ratio of valid pixels in patch [0, 1]", Double.class),
                                /*15*/
                new FeatureType("fractal_index", "Fractal index estimation [1, 2]", Double.class),
                                /*16*/
                new FeatureType("clumpiness", "A clumpiness index [-1, 1]", Double.class),
        };
    }
}
