package org.esa.rss.pfa.fe.op;

import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.rss.pfa.fe.KmlWriter;

import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Norman Fomferra
 */
public class DefaultFeatureOutput implements FeatureOutput {

    public static final String PATCH_ID_PATTERN = "x%02dy%02d";
    public static final String METADATA_FILE_NAME = "fex-metadata.txt";
    public static final String OVERVIEW_XML_FILE_NAME = "fex-overview.xml";
    public static final String OVERVIEW_XSL_FILE_NAME = "fex-overview.xsl";
    public static final String OVERVIEW_CSS_FILE_NAME = "fex-overview.css";
    public static final String PRODUCT_DIR_NAME_EXTENSION = ".fex";

    private final File productTargetDir;
    private Writer overviewWriter;
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

    private void writeFeatureTypeXml(FeatureType[] featureTypes) throws IOException {
        for (FeatureType featureType : featureTypes) {
            AttributeType[] attributeTypes = featureType.getAttributeTypes();
            if (attributeTypes != null) {
                overviewWriter.write(String.format("<featureType name=\"%s\">\n", featureType.getName()));
                for (AttributeType attributeType : attributeTypes) {
                    overviewWriter.write(String.format("\t<attributeType name=\"%s\" valueType=\"%s\"/>\n", attributeType.getName(), attributeType.getValueType().getSimpleName()));
                }
                overviewWriter.write(String.format("</featureType>\n"));
            } else {
                overviewWriter.write(String.format("<featureType name=\"%s\" valueType=\"%s\"/>\n", featureType.getName(), featureType.getValueType().getSimpleName()));
            }
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
            if (isImageFeatureType(featureType)) {
                KmlWriter kmlWriter = new KmlWriter(new FileWriter(new File(productTargetDir, featureType.getName() + "_overview.kml")),
                                                    sourceProduct.getName(),
                                                    "RGB tiles from reflectances of " + sourceProduct.getName());
                kmlWriters.add(kmlWriter);
            }
        }

        copyResource(getClass(), OVERVIEW_XSL_FILE_NAME, productTargetDir);
        copyResource(getClass(), OVERVIEW_CSS_FILE_NAME, productTargetDir);

        overviewWriter = new FileWriter(new File(productTargetDir, OVERVIEW_XML_FILE_NAME));
        overviewWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        overviewWriter.write("<?xml-stylesheet type=\"text/xsl\" href=\"fex-overview.xsl\"?>\n");
        overviewWriter.write("<featureExtraction source=\"" + sourceProduct.getName() + "\">\n");
        writeFeatureTypeXml(featureTypes);
    }

    private static void copyResource(Class<? extends DefaultFeatureOutput> aClass, String resourceName, File targetDir) throws IOException {
        InputStream xslIs = aClass.getResourceAsStream(resourceName);
        OutputStream xslOs = new FileOutputStream(new File(targetDir, resourceName));
        byte[] bytes = new byte[16 * 1024];
        int len;
        while ((len = xslIs.read(bytes)) > 0) {
            xslOs.write(bytes, 0, len);
        }
        xslOs.close();
        xslIs.close();
    }

    private boolean isImageFeatureType(FeatureType featureType) {
        return RenderedImage.class.isAssignableFrom(featureType.getValueType());
    }

    private boolean isProductFeatureType(FeatureType featureType) {
        return Product.class.isAssignableFrom(featureType.getValueType());
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
                    if (isImageFeatureType(feature.getFeatureType())) {
                        float w = patchProduct.getSceneRasterWidth();
                        float h = patchProduct.getSceneRasterHeight();
                        // quadPositions: counter clockwise lon,lat coordinates starting at lower-left
                        GeoPos[] quadPositions = new GeoPos[]{
                                patchProduct.getGeoCoding().getGeoPos(new PixelPos(0, h), null),
                                patchProduct.getGeoCoding().getGeoPos(new PixelPos(w, h), null),
                                patchProduct.getGeoCoding().getGeoPos(new PixelPos(w, 0), null),
                                patchProduct.getGeoCoding().getGeoPos(new PixelPos(0, 0), null),
                        };
                        String imagePath = patchId + "/" + feature.getName() + ".png";
                        kmlWriters.get(kmlWriterIndex).writeGroundOverlayEx(patchId, quadPositions, imagePath);
                        kmlWriterIndex++;
                    }
                } else {
                    writeFeatureProperties(feature, writer);
                }
            }
        } finally {
            writer.close();
        }

        //////////////////////////////////
        // todo: this is sloppy code: let the feature writer do this, pass the FeatureOutput context into FeatureWriter.writeFeature(...)
        //
        writePatchXml(patchId, patchX, patchY, features);
        //
        //////////////////////////////////
    }

    private void writeFeatureProperties(Feature feature, Writer writer) throws IOException {
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

    private void writeProductFeatureXml(Feature feature, String productPath) throws IOException {
        overviewWriter.write(String.format("\t<feature name=\"%s\" type=\"raw\">%s</feature>\n", feature.getName(), productPath));
    }

    private void writeImageFeatureXml(Feature feature, String imagePath) throws IOException {
        overviewWriter.write(String.format("\t<feature name=\"%s\" type=\"img\">%s</feature>\n", feature.getName(), imagePath));
    }

    private void writeAttributedFeatureXml(Feature feature) throws IOException {
        Object[] attributeValues = feature.getAttributeValues();
        if (attributeValues != null && attributeValues.length > 0) {
            overviewWriter.write(String.format("\t<feature name=\"%s\">\n", feature.getName()));
            for (int i = 0; i < attributeValues.length; i++) {
                Object value = attributeValues[i];
                String tagName = feature.getFeatureType().getAttributeTypes()[i].getName();
                if (value instanceof Double || value instanceof Float) {
                    overviewWriter.write(String.format("\t\t<%s>%.4f</%s>\n", tagName, value, tagName));
                } else if (value != null) {
                    overviewWriter.write(String.format("\t\t<%s>%s</%s>\n", tagName, value, tagName));
                } else {
                    overviewWriter.write(String.format("\t\t<%s/>\n", tagName));
                }
            }
            overviewWriter.write(String.format("\t</feature>\n"));
        } else {
            Object value = feature.getValue();
            if (value instanceof Double || value instanceof Float) {
                overviewWriter.write(String.format("\t<feature name=\"%s\">%.4f</feature>\n", feature.getName(), value));
            } else if (value != null) {
                overviewWriter.write(String.format("\t<feature name=\"%s\">%s</feature>\n", feature.getName(), value));
            } else {
                overviewWriter.write(String.format("\t<feature name=\"%s\"/>\n", feature.getName()));
            }
        }
    }

    private void writePatchXml(String patchId, int patchX, int patchY, Feature[] features) throws IOException {
        overviewWriter.write(String.format("<patch id=\"%s\" patchX=\"%s\" patchY=\"%s\">\n", patchId, patchX, patchY));
        for (Feature feature : features) {
            if (isImageFeatureType(feature.getFeatureType())) {
                writeImageFeatureXml(feature, patchId + "/" + feature.getName() + ".png");
            } else if (isProductFeatureType(feature.getFeatureType())) {
                writeProductFeatureXml(feature, patchId + "/" + feature.getName() + ".dim");
            } else {
                writeAttributedFeatureXml(feature);
            }
        }
        overviewWriter.write("</patch>\n");
    }


    @Override
    public void close() throws IOException {
        overviewWriter.write("</featureExtraction>\n");
        overviewWriter.close();
        for (KmlWriter kmlWriter : kmlWriters) {
            kmlWriter.close();
        }
    }
}
