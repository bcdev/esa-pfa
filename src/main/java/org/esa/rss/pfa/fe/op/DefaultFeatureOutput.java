package org.esa.rss.pfa.fe.op;

import org.esa.beam.framework.datamodel.Product;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/**
* @author Norman Fomferra
*/
public class DefaultFeatureOutput implements FeatureOutput {
    final File fexTargetDir;

    public DefaultFeatureOutput(File fexTargetDir) {
        this.fexTargetDir = fexTargetDir;
    }

    public static void writeFeatureTypes(FeatureType[] featureTypes, Writer writer) throws IOException {
        writer.write(String.format("featureTypes.length = %d\n", featureTypes.length));
        for (int i = 0; i < featureTypes.length; i++) {
            FeatureType featureType = featureTypes[i];
            writeFeatureType(featureType, i, writer);
        }
    }

    public static void writeFeatureType(FeatureType featureType, int i, Writer writer) throws IOException {
        writer.write("#\n");
        writer.write(String.format("# Feature '%s'\n", featureType.getName()));
        writer.write("#\n");
        writer.write(String.format("featureTypes.%d.name = %s\n", i, featureType.getName()));
        writer.write(String.format("featureTypes.%d.description = %s\n", i, featureType.getDescription()));
        writer.write(String.format("featureTypes.%d.type = %s\n", i, featureType.getValueType()));
    }

    public static void writeFeatures(Feature[] features, Writer writer) throws IOException {
        for (Feature feature : features) {
            writeFeature(feature, writer);
        }
    }

    public static void writeFeature(Feature feature, Writer writer) throws IOException {
        Object value = feature.getValue();
        if (value != null) {
            writer.write(String.format("%s = %s\n",
                                       feature.getFeatureType().getName(), value));
        }
        Object[] attributeValues = feature.getAttributeValues();
        if (attributeValues != null) {
            for (int i = 0; i < attributeValues.length; i++) {
                Object attributeValue = attributeValues[i];
                writer.write(String.format("%s.%s = %s\n",
                                           feature.getFeatureType().getName(),
                                           feature.getFeatureType().getAttributeTypes()[i].getName(),
                                           attributeValue));
            }
        }
    }

    @Override
    public void writeMetadata(FeatureType... featureTypes) throws IOException {

        File file = new File(fexTargetDir, "fex-metadata.txt");
        Writer writer = new FileWriter(file);

        try {
            writeFeatureTypes(featureTypes, writer);
        } finally {
            writer.close();
        }
    }

    @Override
    public void writePatchFeatures(int patchX, int patchY, Product product, Feature... features) throws IOException {
        final File patchTargetDir = new File(fexTargetDir, String.format("x%02dy%02d", patchX, patchY));
        if (!patchTargetDir.mkdir()) {
            throw new IOException(String.format("Failed to create directory '%s'", patchTargetDir));
        }

        File file = new File(patchTargetDir, "features.txt");
        Writer writer = new FileWriter(file);
        try {
            writeFeatures(features, writer);
        } finally {
            writer.close();
        }
    }

    @Override
    public void close() {
    }
}
