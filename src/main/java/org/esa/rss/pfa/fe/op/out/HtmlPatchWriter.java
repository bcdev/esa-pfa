package org.esa.rss.pfa.fe.op.out;

import org.esa.beam.framework.datamodel.Product;
import org.esa.rss.pfa.fe.op.AttributeType;
import org.esa.rss.pfa.fe.op.Feature;
import org.esa.rss.pfa.fe.op.FeatureType;
import org.esa.rss.pfa.fe.op.Patch;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * @author Norman Fomferra
 */
public class HtmlPatchWriter implements PatchWriter {

    private static final String OVERVIEW_HTML_FILE_NAME = "fex-overview.html";
    private static final String OVERVIEW_JS_FILE_NAME = "fex-overview.js";
    private static final String OVERVIEW_CSS_FILE_NAME = "fex-overview.css";

    private final File productTargetDir;
    private Writer htmlWriter;
    private Product sourceProduct;
    private String[] labelNames;
    private FeatureType[] featureTypes;
    private int patchIndex;

    public HtmlPatchWriter(File productTargetDir) throws IOException {
        this.productTargetDir = productTargetDir;
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

    @Override
    public void initialize(Product sourceProduct, String[] labelNames, FeatureType... featureTypes) throws IOException {
        this.sourceProduct = sourceProduct;
        this.labelNames = labelNames;
        this.featureTypes = featureTypes;

        PatchWriterHelpers.copyResource(getClass(), OVERVIEW_JS_FILE_NAME, productTargetDir);
        PatchWriterHelpers.copyResource(getClass(), OVERVIEW_CSS_FILE_NAME, productTargetDir);

        htmlWriter = new FileWriter(new File(productTargetDir, OVERVIEW_HTML_FILE_NAME));
        htmlWriter.write("<!DOCTYPE HTML>\n");
        htmlWriter.write("<html>\n");

        htmlWriter.write("<head>\n");
        htmlWriter.write("\t<meta http-equiv=\"content-type\" content=\"text/html; charset=ISO-8859-1\">\n");
        htmlWriter.write(String.format("\t<title>%s</title>\n", this.sourceProduct.getName()));
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
        for (FeatureType featureType : this.featureTypes) {
            if (!PatchWriterHelpers.isProductFeatureType(featureType)) {
                htmlWriter.write(String.format("\t<th class=\"ftHead\">%s</th>\n", featureType.getName()));
            }
        }
        if (this.labelNames != null) {
            htmlWriter.write(String.format("\t<th class=\"ftHead\">label</th>\n"));
        }
        htmlWriter.write(String.format("</tr>\n"));
    }


    @Override
    public void writePatch(Patch patch, Feature... features) throws IOException {

        htmlWriter.write(String.format("<tr class=\"fRow\">\n"));
        htmlWriter.write(String.format("\t<td class=\"fValue\">%s</td>\n", patch.getPatchName()));
        for (Feature feature : features) {
            if (PatchWriterHelpers.isImageFeatureType(feature.getFeatureType())) {
                writeImageFeatureHtml(feature, patch.getPatchName() + "/" + feature.getName() + ".png");
            } else if (PatchWriterHelpers.isProductFeatureType(feature.getFeatureType())) {
                // ignore
            } else {
                writeAttributedFeatureHtml(feature);
            }
        }
        writeLabelSelector(patch.getPatchName());
        htmlWriter.write(String.format("</tr>\n"));

        patchIndex++;
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
        htmlWriter.write("</table>\n");
        htmlWriter.write("<div><input type=\"button\" value=\"Show Labels\" onclick=\"fex_openCsv(window.document); return false\"></div>\n");
        htmlWriter.write("</form>\n");
        htmlWriter.write("</body>\n");
        htmlWriter.write("</html>\n");
        htmlWriter.close();
    }

}
