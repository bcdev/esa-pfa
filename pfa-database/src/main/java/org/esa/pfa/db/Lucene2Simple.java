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

package org.esa.pfa.db;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.esa.pfa.fe.AbstractApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.fe.op.DatasetDescriptor;
import org.esa.pfa.fe.op.FeatureType;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 * Converts a Lucene index into a very tiny data file.
 * @author marcoz
 */
public class Lucene2Simple {

    private final Path lucenePath;
    private final Path simpleDbFeaturesPath;
    private final Path simpleDbNamesPath;
    private final FeatureType[] effectiveFeatureTypes;

    public Lucene2Simple(Path dsDescriptorPath, Path lucenePath, Path simpleDbFeaturesPath, Path simpleDbNamesPath) throws IOException {
        this.lucenePath = lucenePath;
        this.simpleDbFeaturesPath = simpleDbFeaturesPath;
        this.simpleDbNamesPath = simpleDbNamesPath;
        // read ds descriptor
        DatasetDescriptor dsDescriptor = DatasetDescriptor.read(dsDescriptorPath.toFile());
        effectiveFeatureTypes = getEffectiveFeatureTypes(dsDescriptor);
    }

    private void run() throws IOException {
        ArrayList<String> productNames = new ArrayList<>();
        Map<String, Integer> productNamesIndex = new HashMap<>();

        try (
                Directory indexDirectory = new SimpleFSDirectory(lucenePath.toFile());
                DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(simpleDbFeaturesPath.toFile())))
        ) {
            IndexReader indexReader = DirectoryReader.open(indexDirectory);
            int numDocs = indexReader.numDocs();
            dos.writeInt(numDocs);

            for (int i = 0; i < numDocs; i++) {
                Document doc = indexReader.document(i);

                String productName = doc.getValues("product")[0];
                if (productName.endsWith(".fex")) {
                    productName = productName.substring(0, productName.length() - 4);
                }

                Integer index = productNamesIndex.get(productName);
                if (index == null) {
                    index = productNames.size();
                    productNames.add(productName);
                    productNamesIndex.put(productName, index);
                }
                dos.writeInt(index.intValue());
                dos.writeInt(Integer.parseInt(doc.getValues("px")[0]));
                dos.writeInt(Integer.parseInt(doc.getValues("py")[0]));

                for (FeatureType feaType : effectiveFeatureTypes) {
                    final String[] values = doc.getValues(feaType.getName());
                    if (values != null && values.length > 0) {
                        final Class<?> valueType = feaType.getValueType();
                        if (Double.class.isAssignableFrom(valueType)) {
                            dos.writeDouble(Double.parseDouble(values[0]));
                        } else if (Integer.class.isAssignableFrom(valueType)) {
                            int value = Integer.parseInt(values[0]);
                            dos.writeInt(value);
                        } else {
                            throw new IllegalArgumentException("valueType '" + valueType + "' not supported (yet).");
                        }
                    } else {
                        throw new IllegalArgumentException("feature for '" + feaType.getName()+ "' missing.");
                    }
                }
                if (i % 100000 == 0) {
                    System.out.printf("progress = %5.2f%n", (i / (double) numDocs) * 100);
                }
            }
            System.out.println("converted " + numDocs + " entries form Lucene to Simple format.");
        }
        try (
                DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(simpleDbNamesPath.toFile())))
        ) {
            System.out.println("# productNames = " + productNames.size());

            dos.writeInt(productNames.size());
            for (String productName : productNames) {
                dos.writeUTF(productName);
            }
        }
        System.out.println("Conversion completed.");

    }

    static FeatureType[] getEffectiveFeatureTypes(DatasetDescriptor dsDescriptor) {
        String appName = dsDescriptor.getName();
        PFAApplicationDescriptor applicationDescriptor = PFAApplicationRegistry.getInstance().getDescriptorByName(appName);
        if (applicationDescriptor == null) {
            throw new IllegalArgumentException("Unknown application " + appName);
        }
        Set<String> defaultFeatureSet = applicationDescriptor.getDefaultFeatureSet();
        FeatureType[] featureTypes = dsDescriptor.getFeatureTypes();
        return AbstractApplicationDescriptor.getEffectiveFeatureTypes(featureTypes, defaultFeatureSet);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Lucene2Simple <db-dir>");
        }
        Path dbPath = Paths.get(args[0]);
        System.out.println("dbPath = " + dbPath);

        Path dsDescriptorPath = dbPath.resolve("ds-descriptor.xml");
        if (!Files.exists(dsDescriptorPath)) {
            throw new IllegalArgumentException("ds-descriptor.xml missing: " + dsDescriptorPath);
        }
        Path lucenePath = dbPath.resolve(DsIndexerTool.DEFAULT_INDEX_NAME);
        if (!Files.exists(lucenePath)) {
            throw new IllegalArgumentException("lucene dir missing: " + lucenePath);
        }
        Path simpleDbFeatures = dbPath.resolve(SimplePatchQuery.FEATURE_DB);
        Path simpleDbNames = dbPath.resolve(SimplePatchQuery.NAME_DB);

        Lucene2Simple lucene2Simple = new Lucene2Simple(dsDescriptorPath, lucenePath, simpleDbFeatures, simpleDbNames);
        lucene2Simple.run();
    }
}
