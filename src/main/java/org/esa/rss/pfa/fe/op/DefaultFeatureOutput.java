package org.esa.rss.pfa.fe.op;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * @author Norman Fomferra
 */
public class DefaultFeatureOutput implements FeatureOutput {

    public static final String PATCH_ID_PATTERN = "x%02dy%02d";
    public static final String METADATA_FILE_NAME = "fex-metadata.txt";
    public static final String PRODUCT_DIR_NAME_EXTENSION = ".fex";

    private final FeatureOutputFactory featureOutputFactory;
    private final File productTargetDir;

    public DefaultFeatureOutput(FeatureOutputFactory featureOutputFactory, String productName) throws IOException {
        this.featureOutputFactory = featureOutputFactory;

        String targetPath = featureOutputFactory.getTargetPath();
        if (targetPath == null) {
            targetPath = ".";
        }

        File targetDir = new File(targetPath).getAbsoluteFile();

        if (targetDir.exists()) {
            if (!featureOutputFactory.isOverwriteMode()) {
                String[] contents = targetDir.list();
                if (contents != null && contents.length > 0) {
                    for (String content : contents) {
                        System.out.println("content = " + content);
                    }
                    throw new IOException(String.format("Directory is not empty: '%s'", targetDir));
                }
            }
        } else {
            if (!featureOutputFactory.isOverwriteMode()) {
                throw new IOException(String.format("Directory does not exist: '%s'", targetDir));
            } else {
                if (!targetDir.mkdirs()) {
                    throw new IOException(String.format("Failed to create directory '%s'", targetDir));
                }
            }
        }

        File productTargetDir = new File(targetDir, productName + DefaultFeatureOutput.PRODUCT_DIR_NAME_EXTENSION);
        if (!productTargetDir.exists()) {
            if (!productTargetDir.mkdir()) {
                throw new IOException(String.format("Failed to create directory '%s'", productTargetDir));
            }
        }

        this.productTargetDir = productTargetDir;
    }

    public static void writeFeatureTypes(FeatureType[] featureTypes, Writer writer) throws IOException {
        writer.write(String.format("featureTypes.length = %d%n", featureTypes.length));
        for (int i = 0; i < featureTypes.length; i++) {
            FeatureType featureType = featureTypes[i];
            writeFeatureType(featureType, i, writer);
        }
    }

    public static void writeFeatureType(FeatureType featureType, int i, Writer writer) throws IOException {
        writer.write("#\n");
        writer.write(String.format("# Feature '%s'%n", featureType.getName()));
        writer.write("#\n");
        writer.write(String.format("featureTypes.%d.name = %s%n", i, featureType.getName()));
        writer.write(String.format("featureTypes.%d.description = %s%n", i, featureType.getDescription()));
        AttributeType[] attributeTypes = featureType.getAttributeTypes();
        if (attributeTypes != null) {
            writer.write(String.format("featureTypes.%d.attributeTypes.length = %s%n", i, attributeTypes.length));
            for (int j = 0; j < attributeTypes.length; j++) {
                AttributeType attributeType = attributeTypes[j];
                writer.write(String.format("featureTypes.%d.attributeTypes.%d.name = %s%n", i, j, attributeType.getName()));
                writer.write(String.format("featureTypes.%d.attributeTypes.%d.description = %s%n", i, j, attributeType.getDescription()));
                writer.write(String.format("featureTypes.%d.attributeTypes.%d.valueType = %s%n", i, j, attributeType.getValueType().getSimpleName()));
            }
        } else {
            writer.write(String.format("featureTypes.%d.valueType = %s%n", i, featureType.getValueType().getSimpleName()));
        }
    }

    public static void writeFeatures(Feature[] features, Writer writer) throws IOException {
        for (Feature feature : features) {
            writeFeature(feature, writer);
        }
    }

    public static void writeFeature(Feature feature, Writer writer) throws IOException {
        Object[] attributeValues = feature.getAttributeValues();
        if (attributeValues != null) {
            for (int i = 0; i < attributeValues.length; i++) {
                Object attributeValue = attributeValues[i];
                writer.write(String.format("%s.%s = %s%n",
                                           feature.getFeatureType().getName(),
                                           feature.getFeatureType().getAttributeTypes()[i].getName(),
                                           attributeValue));
            }
        } else {
            writer.write(String.format("%s = %s%n",
                                       feature.getFeatureType().getName(), feature.getValue()));
        }
    }

    @Override
    public void writeMetadata(FeatureType... featureTypes) throws IOException {

        File file = new File(productTargetDir, METADATA_FILE_NAME);
        Writer writer = new FileWriter(file);

        try {
            writeFeatureTypes(featureTypes, writer);
        } finally {
            writer.close();
        }

        if (!featureOutputFactory.getSkipQuicklookOutput()) {
            // todo - write KML
        }
    }

    @Override
    public void writePatchData(int patchX, int patchY, Product product, Feature... features) throws IOException {
        String patchId = String.format(PATCH_ID_PATTERN, patchX, patchY);

        final File patchTargetDir = new File(productTargetDir, patchId);
        if (!patchTargetDir.mkdir()) {
            throw new IOException(String.format("Failed to create directory '%s'", patchTargetDir));
        }

        if (!featureOutputFactory.getSkipFeatureOutput()) {
            File file = new File(patchTargetDir, "features.txt");
            Writer writer = new FileWriter(file);
            try {
                writeFeatures(features, writer);
            } finally {
                writer.close();
            }
        }

        if (!featureOutputFactory.getSkipProductOutput()) {
            ProductIO.writeProduct(product, new File(patchTargetDir, product.getName() + ".dim"), "BEAM-DIMAP", false);
        }

        if (!featureOutputFactory.getSkipQuicklookOutput()) {
            // todo - write RGB
        }
    }

    @Override
    public void close() {
        if (!featureOutputFactory.getSkipQuicklookOutput()) {
            // todo - close KML
        }
    }
}
