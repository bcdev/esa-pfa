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
    public static final String OVERVIEW_HTML_FILE_NAME = "fex-overview.html";
    public static final String OVERVIEW_JS_FILE_NAME = "fex-overview.js";
    public static final String OVERVIEW_XML_FILE_NAME = "fex-overview.xml";
    public static final String OVERVIEW_XSL_FILE_NAME = "fex-overview.xsl";
    public static final String OVERVIEW_CSS_FILE_NAME = "fex-overview.css";
    public static final String PRODUCT_DIR_NAME_EXTENSION = ".fex";

    private final File productTargetDir;
    private Writer xmlWriter;
    private List<KmlWriter> kmlWriters;
    private final boolean overwriteMode;
    private Writer htmlWriter;
    private Product sourceProduct;
    private String[] labelNames;
    private FeatureType[] featureTypes;
    private int patchIndex;

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
        if (featureType.hasAttributes()) {
            AttributeType[] attributeTypes = featureType.getAttributeTypes();
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

    private void writeFeatureTypeXml() throws IOException {
        for (FeatureType featureType : featureTypes) {
            if (featureType.hasAttributes()) {
                AttributeType[] attributeTypes = featureType.getAttributeTypes();
                xmlWriter.write(String.format("<featureType name=\"%s\">\n", featureType.getName()));
                for (AttributeType attributeType : attributeTypes) {
                    xmlWriter.write(String.format("\t<attributeType name=\"%s\" valueType=\"%s\"/>\n", attributeType.getName(), attributeType.getValueType().getSimpleName()));
                }
                xmlWriter.write(String.format("</featureType>\n"));
            } else {
                xmlWriter.write(String.format("<featureType name=\"%s\" valueType=\"%s\"/>\n", featureType.getName(), featureType.getValueType().getSimpleName()));
            }
        }

    }

    private void writeFeatureTypeHtml() throws IOException {
        htmlWriter.write("<table id=\"ftTable\" class=\"ftTable\">\n");
        htmlWriter.write("<tr class=\"ftRow\">\n" +
                                 "\t<th class=\"ftHead\">Name</th>\n" +
                                 "\t<th class=\"ftHead\">Type</th>\n" +
                                 "\t<th class=\"ftHead\">Description</th>\n" +
                                 "</tr>\n");
        for (FeatureType featureType : featureTypes) {
            htmlWriter.write("<tr class=\"ftRow\">\n");
            htmlWriter.write(String.format("" +
                                                   "\t<td class=\"ftName\">%s</td>\n" +
                                                   "\t<td class=\"ftType\">%s</td>\n" +
                                                   "\t<td class=\"ftDescription\">%s</td>\n",
                                           featureType.getName(),
                                           featureType.getValueType().getSimpleName(),
                                           featureType.getDescription()));

            htmlWriter.write("</tr>\n");
        }
        htmlWriter.write("</table>\n");
    }

    private void writeLabelNamesXml() throws IOException {
        if (labelNames != null && labelNames.length > 0) {
            xmlWriter.write("<labels>\n");
            for (String labelName : labelNames) {
                xmlWriter.write(String.format("\t<label name=\"%s\"/>\n", labelName));
            }
            xmlWriter.write("</labels>\n");
        }
    }

    @Override
    public void initialize(Product sourceProduct, String[] labelNames, FeatureType... featureTypes) throws IOException {
        this.sourceProduct = sourceProduct;
        this.labelNames = labelNames;
        this.featureTypes = featureTypes;

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

        copyResource(getClass(), OVERVIEW_JS_FILE_NAME, productTargetDir);
        copyResource(getClass(), OVERVIEW_XSL_FILE_NAME, productTargetDir);
        copyResource(getClass(), OVERVIEW_CSS_FILE_NAME, productTargetDir);

        openXmlWriter();
        openHtmlWriter();
    }

    private void openXmlWriter() throws IOException {
        xmlWriter = new FileWriter(new File(productTargetDir, OVERVIEW_XML_FILE_NAME));
        xmlWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xmlWriter.write("<?xml-stylesheet type=\"text/xsl\" href=\"fex-overview.xsl\"?>\n");
        xmlWriter.write("<featureExtraction source=\"" + sourceProduct.getName() + "\">\n");
        writeFeatureTypeXml();
        writeLabelNamesXml();
    }

    private void openHtmlWriter() throws IOException {
        htmlWriter = new FileWriter(new File(productTargetDir, OVERVIEW_HTML_FILE_NAME));
        htmlWriter.write("<!DOCTYPE HTML>\n");
        htmlWriter.write("<html>\n");

        htmlWriter.write("<head>\n");
        htmlWriter.write("\t<meta http-equiv=\"content-type\" content=\"text/html; charset=ISO-8859-1\">\n");
        htmlWriter.write(String.format("\t<title>%s</title>\n", sourceProduct.getName()));
        htmlWriter.write(String.format("\t<link rel=\"stylesheet\" type=\"text/css\" href=\"%s\"/>\n", OVERVIEW_CSS_FILE_NAME));
        htmlWriter.write(String.format("\t<script src=\"%s\" type=\"text/javascript\"></script>\n", OVERVIEW_JS_FILE_NAME));
        htmlWriter.write("</head>\n");
        htmlWriter.write("<body>\n");

        htmlWriter.write("<p class=\"title\">Feature Types</p>\n");
        writeFeatureTypeHtml();

        htmlWriter.write("<p class=\"title\">Features</p>\n");
        htmlWriter.write("<form>\n");
        htmlWriter.write("<table id=\"fTable\" class=\"fTable\">\n");

        htmlWriter.write(String.format("<tr class=\"fRow\">\n"));
        htmlWriter.write(String.format("\t<th class=\"ftHead\">patch</th>\n"));
        for (FeatureType featureType : featureTypes) {
            if (!isProductFeatureType(featureType)) {
                htmlWriter.write(String.format("\t<th class=\"ftHead\">%s</th>\n", featureType.getName()));
            }
        }
        if (labelNames != null) {
            htmlWriter.write(String.format("\t<th class=\"ftHead\">label</th>\n"));
        }
        htmlWriter.write(String.format("</tr>\n"));
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
        writePatchHtml(patchId, patchX, patchY, features);
        //
        //////////////////////////////////

        patchIndex++;
    }

    private void writeFeatureProperties(Feature feature, Writer writer) throws IOException {
        if (feature.hasAttributes()) {
            Object[] attributeValues = feature.getAttributeValues();
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
        xmlWriter.write(String.format("\t<feature name=\"%s\" type=\"raw\">%s</feature>\n", feature.getName(), productPath));
    }

    private void writeImageFeatureXml(Feature feature, String imagePath) throws IOException {
        xmlWriter.write(String.format("\t<feature name=\"%s\" type=\"img\">%s</feature>\n", feature.getName(), imagePath));
    }

    private void writeAttributedFeatureXml(Feature feature) throws IOException {
        if (feature.hasAttributes()) {
            Object[] attributeValues = feature.getAttributeValues();
            xmlWriter.write(String.format("\t<feature name=\"%s\">\n", feature.getName()));
            for (int i = 0; i < attributeValues.length; i++) {
                Object value = attributeValues[i];
                String tagName = feature.getFeatureType().getAttributeTypes()[i].getName();
                if (value instanceof Double || value instanceof Float) {
                    xmlWriter.write(String.format("\t\t<%s>%.5f</%s>\n", tagName, value, tagName));
                } else if (value != null) {
                    xmlWriter.write(String.format("\t\t<%s>%s</%s>\n", tagName, value, tagName));
                } else {
                    xmlWriter.write(String.format("\t\t<%s/>\n", tagName));
                }
            }
            xmlWriter.write(String.format("\t</feature>\n"));
        } else {
            Object value = feature.getValue();
            if (value instanceof Double || value instanceof Float) {
                xmlWriter.write(String.format("\t<feature name=\"%s\">%.5f</feature>\n", feature.getName(), value));
            } else if (value != null) {
                xmlWriter.write(String.format("\t<feature name=\"%s\">%s</feature>\n", feature.getName(), value));
            } else {
                xmlWriter.write(String.format("\t<feature name=\"%s\"/>\n", feature.getName()));
            }
        }
    }

    private void writePatchXml(String patchId, int patchX, int patchY, Feature[] features) throws IOException {
        xmlWriter.write(String.format("<patch id=\"%s\" patchX=\"%s\" patchY=\"%s\">\n", patchId, patchX, patchY));
        for (Feature feature : features) {
            if (isImageFeatureType(feature.getFeatureType())) {
                writeImageFeatureXml(feature, patchId + "/" + feature.getName() + ".png");
            } else if (isProductFeatureType(feature.getFeatureType())) {
                writeProductFeatureXml(feature, patchId + "/" + feature.getName() + ".dim");
            } else {
                writeAttributedFeatureXml(feature);
            }
        }
        xmlWriter.write("</patch>\n");
    }

    private void writePatchHtml(String patchId, int patchX, int patchY, Feature[] features) throws IOException {
        htmlWriter.write(String.format("<tr class=\"fRow\">\n"));
        htmlWriter.write(String.format("\t<td class=\"fValue\">%s</td>\n", patchId));
        for (Feature feature : features) {
            if (isImageFeatureType(feature.getFeatureType())) {
                writeImageFeatureHtml(feature, patchId + "/" + feature.getName() + ".png");
            } else if (isProductFeatureType(feature.getFeatureType())) {
                // ignore
            } else {
                writeAttributedFeatureHtml(feature);
            }
        }
        writeLabelSelector(patchId);
        htmlWriter.write(String.format("</tr>\n"));
    }

    private void writeLabelSelector(String patchId) throws IOException {
        htmlWriter.write("\t<td class=\"fValue\">\n");
        htmlWriter.write(String.format("\t<select id=\"label%s\" name=\"%s\" class=\"fLabel\" multiple>\n", patchIndex, patchId));
        for (int i = 0; i < labelNames.length; i++) {
            String labelName = labelNames[i];
            htmlWriter.write(String.format("\t<option value=\"%d\">%s</option>\n", i, labelName));
        }
        htmlWriter.write("\t</select>\n");
        htmlWriter.write("\t</td>\n");
    }

    private void writeImageFeatureHtml(Feature feature, String imagePath) throws IOException {
        htmlWriter.write(String.format("\t<td class=\"fImage\"><img src=\"%s\" alt=\"%s\"/></td>\n", imagePath, feature.getName()));
    }

    private void writeAttributedFeatureHtml(Feature feature) throws IOException {
        if (feature.hasAttributes()) {
            htmlWriter.write(String.format("\t<td class=\"fValue\">\n"));
            Object[] attributeValues = feature.getAttributeValues();
            htmlWriter.write(String.format("\t<table>\n"));
            for (int i = 0; i < attributeValues.length; i++) {
                Object value = attributeValues[i];
                String attrName = feature.getFeatureType().getAttributeTypes()[i].getName();
                htmlWriter.write(String.format("\t\t<tr class=\"fValue\">\n"));
                htmlWriter.write(String.format("\t\t\t<td class=\"fAttrName\">%s<td>\n", attrName));
                htmlWriter.write(String.format("\t\t\t<td class=\"fAttrValue\">"));
                writeValueHtml(value);
                htmlWriter.write(String.format("</td>\n"));
                htmlWriter.write(String.format("\t\t</tr>\n"));
            }
            htmlWriter.write(String.format("\t</table>\n"));
            htmlWriter.write(String.format("\t</td>\n"));
        } else {
            Object value = feature.getValue();
            htmlWriter.write(String.format("\t<td class=\"fValue\">"));
            writeValueHtml(value);
            htmlWriter.write(String.format("</td>\n"));
        }
    }

    private void writeValueHtml(Object value) throws IOException {
        if (value instanceof Float) {
            htmlWriter.write(String.format("%.5f", (Float) value));
        } else if (value instanceof Double) {
            htmlWriter.write(String.format("%.5f", (Double) value));
        } else if (value != null) {
            htmlWriter.write(value.toString());
        }
    }

    @Override
    public void close() throws IOException {
        closeXmlWriter();
        closeHtmlWriter();
        for (KmlWriter kmlWriter : kmlWriters) {
            kmlWriter.close();
        }
        sourceProduct = null;
    }

    private void closeXmlWriter() throws IOException {
        xmlWriter.write("</featureExtraction>\n");
        xmlWriter.close();
    }

    private void closeHtmlWriter() throws IOException {
        htmlWriter.write("</table>\n");
        htmlWriter.write("<div><input type=\"button\" value=\"Show Labels\" onclick=\"fex_openCsv(window.document); return false\"></div>\n");
        htmlWriter.write("</form>\n");
        htmlWriter.write("</body>\n");
        htmlWriter.write("</html>\n");
        htmlWriter.close();
    }
}
