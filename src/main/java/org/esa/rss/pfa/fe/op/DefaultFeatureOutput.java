package org.esa.rss.pfa.fe.op;

import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.rss.pfa.fe.KmlWriter;

import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Norman Fomferra
 */
public class DefaultFeatureOutput implements FeatureOutput {

    public static final String PATCH_ID_PATTERN = "x%02dy%02d";
    public static final String METADATA_FILE_NAME = "fex-metadata.txt";
    public static final String KML_OVERVIEW_FILE_NAME = "fex-overview.kml";
    public static final String PRODUCT_DIR_NAME_EXTENSION = ".fex";

    private final File productTargetDir;
    private List<KmlWriter> kmlWriters;
    private final boolean overwriteMode;

    public DefaultFeatureOutput(FeatureOutputFactory featureOutputFactory, String productName) throws IOException {

        String targetPath = featureOutputFactory.getTargetPath();
        if (targetPath == null) {
            targetPath = ".";
        }

        File targetDir = new File(targetPath).getAbsoluteFile();

        overwriteMode = featureOutputFactory.isOverwriteMode();
        if (targetDir.exists()) {
            if (!overwriteMode) {
                String[] contents = targetDir.list();
                if (contents != null && contents.length > 0) {
                    throw new IOException(String.format("Directory is not empty: '%s'", targetDir));
                }
            }
        } else {
            if (!overwriteMode) {
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

    @Override
    public void initialize(Product sourceProduct, FeatureType... featureTypes) throws IOException {
        Writer metadataWriter = new FileWriter(new File(productTargetDir, METADATA_FILE_NAME));
        try {
            writeFeatureTypes(featureTypes, metadataWriter);
        } finally {
            metadataWriter.close();
        }

        // todo: this is sloppy code: let the feature writer do this, pass the FeatureOutput context into FeatureWriter.writeFeature(...)
        kmlWriters = new ArrayList<KmlWriter>();
        for (FeatureType featureType : featureTypes) {
            if (isRenderedImageFeatureType(featureType)) {
                KmlWriter kmlWriter = new KmlWriter(new FileWriter(new File(productTargetDir, featureType.getName() + "_overview.kml")),
                                                    sourceProduct.getName(),
                                                    "RGB tiles from reflectances of " + sourceProduct.getName());
                kmlWriters.add(kmlWriter);
            }
        }
    }

    private boolean isRenderedImageFeatureType(FeatureType featureType) {
        return RenderedImage.class.isAssignableFrom(featureType.getValueType());
    }

    @Override
    public void writePatchFeatures(int patchX, int patchY, Product patchProduct, Feature... features) throws IOException {
        String patchId = String.format(PATCH_ID_PATTERN, patchX, patchY);

        final File patchTargetDir = new File(productTargetDir, patchId);
        if (!patchTargetDir.exists()) {
            if (!patchTargetDir.mkdir()) {
                throw new IOException(String.format("Failed to create directory '%s'", patchTargetDir));
            }
        }

        File file = new File(patchTargetDir, "features.txt");
        Writer writer = new FileWriter(file);
        try {
            int kmlWriterIndex = 0;
            for (Feature feature : features) {
                FeatureWriter featureWriter = feature.getExtension(FeatureWriter.class);
                if (featureWriter != null) {
                    featureWriter.writeFeature(feature, patchTargetDir.getPath());

                    // todo: this is sloppy code: let the feature writer do this, pass the FeatureOutput context into FeatureWriter.writeFeature(...)
                    if (isRenderedImageFeatureType(feature.getFeatureType())) {
                        float w = patchProduct.getSceneRasterWidth();
                        float h = patchProduct.getSceneRasterHeight();
                        // quadPositions: counter clockwise lon,lat coordinates starting at lower-left
                        GeoPos[] quadPositions = new GeoPos[]{
                                patchProduct.getGeoCoding().getGeoPos(new PixelPos(0, h), null),
                                patchProduct.getGeoCoding().getGeoPos(new PixelPos(w, h), null),
                                patchProduct.getGeoCoding().getGeoPos(new PixelPos(w, 0), null),
                                patchProduct.getGeoCoding().getGeoPos(new PixelPos(0, 0), null),
                        };
                        kmlWriters.get(kmlWriterIndex).writeGroundOverlayEx(patchId, quadPositions, patchId + "/" + feature.getName() + ".png");
                        kmlWriterIndex++;
                    }

                } else {
                    writeFeature(feature, writer);
                }
            }
        } finally {
            writer.close();
        }

    }

    private void writeFeature(Feature feature, Writer writer) throws IOException {
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
    public void close() throws IOException {
        for (KmlWriter kmlWriter : kmlWriters) {
            kmlWriter.close();
        }
    }
}
