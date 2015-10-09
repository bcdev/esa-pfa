/*
 * Copyright (c) 2013. Brockmann Consult GmbH (info@brockmann-consult.de)
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

import com.bc.ceres.core.ProgressMonitor;
import org.esa.pfa.fe.AlgalBloomApplicationDescriptor;
import org.esa.pfa.fe.FrontsCloudMaskOperator;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.op.DatasetDescriptor;
import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.FeatureWriter;
import org.esa.pfa.fe.op.FeatureWriterResult;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.fe.op.out.PatchSink;
import org.esa.s3tbx.meris.radiometry.equalization.ReprocessingVersion;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.ImageInfo;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.RGBChannelDef;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.graph.Graph;
import org.esa.snap.core.gpf.graph.GraphContext;
import org.esa.snap.core.gpf.graph.GraphException;
import org.esa.snap.core.gpf.graph.GraphIO;
import org.esa.snap.core.gpf.graph.GraphProcessor;
import org.esa.snap.core.image.ImageManager;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.runtime.Config;

import java.awt.Color;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * An operator for extracting algal bloom features.
 *
 * @author Norman Fomferra
 */
@OperatorMetadata(alias = "BiTempFeatureWriter", version = "0.5", autoWriteDisabled = true,
        category = "Raster/Image Analysis/Feature Extraction")
public class BiTempSpectralFeatureWriter extends FeatureWriter {

    public static final int DEFAULT_PATCH_SIZE = 200;

    public static void main(String[] args) {
        String sourcePath = args[0];
        String targetDirPath = args[1];

        System.setProperty("snap.dataio.reader.tileWidth", String.valueOf(DEFAULT_PATCH_SIZE));
        System.setProperty("snap.dataio.reader.tileHeight", String.valueOf(DEFAULT_PATCH_SIZE));
        System.setProperty("snap.parallelism", "1");

        Config.instance().load();

        SystemUtils.init3rdPartyLibs(BiTempSpectralFeatureWriter.class);
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();

        try {
            File sourceFile = new File(sourcePath);
            if (sourceFile.isDirectory()) {
                File[] sourceFiles = sourceFile.listFiles((dir, name) -> {
                    return name.endsWith(".N1");
                });
                if (sourceFiles != null) {
                    for (final File file : sourceFiles) {
                        processGraph(file.getPath(), targetDirPath);
                    }
                }
            } else {
                processGraph(sourceFile.getPath(), targetDirPath);
            }
        } catch (GraphException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static void processGraph(String sourcePath, String targetDir) throws GraphException, IOException {
        BiTempSpectralApplicationDescriptor applicationDescriptor = new BiTempSpectralApplicationDescriptor();
        HashMap<String, String> variables = new HashMap<>();
        variables.put("sourcePath", sourcePath);
        variables.put("targetDir", targetDir);
        processGraph(applicationDescriptor, variables);
    }

    // General form. Move to FeatureWriter.
    private static void processGraph(PFAApplicationDescriptor applicationDescriptor, HashMap<String, String> variables) throws IOException, GraphException {
        Graph graph;
        try (Reader graphReader = new InputStreamReader(applicationDescriptor.getGraphFileAsStream())) {
            graph = GraphIO.read(graphReader, variables);
        }

        ProgressMonitor pm = ProgressMonitor.NULL;

        GraphContext graphContext = new GraphContext(graph);
        new GraphProcessor().executeGraph(graphContext, pm);
        graphContext.dispose();
    }


    public static final String FEX_VALID_MASK = "NOT l1_flags.INVALID AND NOT l1_flags.BRIGHT";
    public static final String FEX_CLOUD_MASK_NAME = "cloud_mask";
    public static final String FEX_ROI_MASK_NAME = "fex_roi";

    @Parameter(defaultValue = "8", description = "Number of successful cloudiness tests for cloud screening ('Fronts' algorithm)")
    private int cloudMaskThreshold;
    @Parameter(defaultValue = "0.2")
    private double minValidPixelRatio;

    private FeatureType[] featureTypes;

    @Override
    protected FeatureType[] getFeatureTypes() {

        if (featureTypes == null) {
            featureTypes = new BiTempSpectralApplicationDescriptor().getFeatureTypes();
        }
        return featureTypes;
    }

    @Override
    public void initialize() throws OperatorException {
        super.initialize();
        writeDatasetDescriptor();
        result = new FeatureWriterResult(getSourceProduct().getName());
    }

    private void writeDatasetDescriptor() {
        File parentFile = getTargetDir().getAbsoluteFile().getParentFile();
        File file = new File(parentFile, "ds-descriptor.xml");
        if (!file.exists()) {
            BiTempSpectralApplicationDescriptor applicationDescriptor = new BiTempSpectralApplicationDescriptor();
            DatasetDescriptor datasetDescriptor = new DatasetDescriptor(applicationDescriptor.getName(), "0.5", "Bi-Temporal Spectral Features", applicationDescriptor.getFeatureTypes());
            try {
                datasetDescriptor.write(file);
            } catch (IOException e) {
                getLogger().warning(e.getMessage());
            }
        }
    }

    @Override
    protected boolean processPatch(Patch patch, PatchSink sink) throws IOException {

        if (skipFeaturesOutput && skipQuicklookOutput && skipProductOutput) {
            return false;
        }

        int patchX = patch.getPatchX();
        int patchY = patch.getPatchY();
        Product patchProduct = patch.getPatchProduct();

        int numPixelsRequired = patchWidth * patchHeight;
        int numPixelsTotal = patchProduct.getSceneRasterWidth() * patchProduct.getSceneRasterHeight();

        double patchPixelRatio = numPixelsTotal / (double) numPixelsRequired;
        if (patchPixelRatio < minValidPixelRatio) {
            getLogger().warning(String.format("Rejected patch x%dy%d, patchPixelRatio=%f%%", patchX, patchY,
                                              patchPixelRatio * 100));
            return false;
        }

        Product featureProduct = createRadiometricallyCorrectedProduct(patchProduct);
        Product wasteProduct = addMasks(featureProduct);
        Mask roiMask = featureProduct.getMaskGroup().get(FEX_ROI_MASK_NAME);

        addTriStimulusBands(featureProduct);

        double validPixelCount = MaskStats.countPixels(roiMask);
        double validPixelRatio = validPixelCount / (double) numPixelsRequired;
        if (validPixelRatio <= minValidPixelRatio) {
            getLogger().warning(String.format("Rejected patch x%dy%d, validPixelRatio = %f%%", patchX, patchY,
                                              validPixelRatio * 100));
            disposeProducts(featureProduct, wasteProduct);
            return false;
        }

        List<Feature> features = new ArrayList<>();

        if (!skipProductOutput) {
            features.add(new Feature(featureTypes[0], featureProduct));
        }

        if (!skipQuicklookOutput) {
            features.add(new Feature(featureTypes[1],
                                     createFixedRangeUnmaskedRgbImage(featureProduct)));
            features.add(new Feature(featureTypes[2],
                                     createDynamicRangeMaskedRgbImage(featureProduct)));
        }

        features.add(createStxFeature(featureTypes[6], featureProduct));
        features.add(createStxFeature(featureTypes[7], featureProduct));
        features.add(createStxFeature(featureTypes[8], featureProduct));
        features.add(createStxFeature(featureTypes[9], featureProduct));
        features.add(createStxFeature(featureTypes[10], featureProduct));
        features.add(createStxFeature(featureTypes[11], featureProduct));
        features.add(createStxFeature(featureTypes[12], featureProduct));
        features.add(new Feature(featureTypes[14], validPixelRatio));

        Feature[] featuresArray = features.toArray(new Feature[features.size()]);
        patch.setFeatures(featuresArray);
        sink.writePatch(patch, featuresArray);

        disposeProducts(featureProduct, wasteProduct);

        return true;
    }

    protected Feature createStxFeature(FeatureType featureType, Product product) {
        final Band band = product.getBand(featureType.getName());
        return createStxFeature(featureType, band);
    }

    /*
     * @return intermediate waste product for later disposal.
     */
    private Product addMasks(Product product) {
        final Product cloudProduct = createFrontsCloudMaskProduct(product);
        ProductUtils.copyBand("cloud_data_ori_or_flag", cloudProduct, product, true);
        ProductUtils.copyMasks(cloudProduct, product);
        addRoiMask(product, FEX_CLOUD_MASK_NAME);
        return cloudProduct;
    }

    private void addRoiMask(Product featureProduct, String cloudMaskName) {
        final String roiExpr = String.format("(%s) AND NOT (%s)", FEX_VALID_MASK, cloudMaskName);
        featureProduct.addMask(FEX_ROI_MASK_NAME, roiExpr,
                               "ROI for pixels used for the feature extraction", Color.green,
                               0.5);
    }

    private Product createFrontsCloudMaskProduct(Product product) {
        final FrontsCloudMaskOperator op = new FrontsCloudMaskOperator();
        op.setSourceProduct(product);
        op.setRoiExpr(FEX_VALID_MASK);
        op.setThreshold(cloudMaskThreshold);
        return op.getTargetProduct();
    }

    private void addTriStimulusBands(Product product) {

        Band r = product.addBand("vis_red", AlgalBloomApplicationDescriptor.R_EXPR);
        Band g = product.addBand("vis_green", AlgalBloomApplicationDescriptor.G_EXPR);
        Band b = product.addBand("vis_blue", AlgalBloomApplicationDescriptor.B_EXPR);
        applyValidPixelExpr("NOT l1_flags.INVALID", r, g, b);

        Band mr = product.addBand("red", AlgalBloomApplicationDescriptor.R_EXPR);
        Band mg = product.addBand("green", AlgalBloomApplicationDescriptor.G_EXPR);
        Band mb = product.addBand("blue", AlgalBloomApplicationDescriptor.B_EXPR);
        applyValidPixelExpr(FEX_ROI_MASK_NAME, mr, mg, mb);
    }

    private void applyValidPixelExpr(String validPixelExpr, RasterDataNode... nodes) {
        for (RasterDataNode node : nodes) {
            node.setValidPixelExpression(validPixelExpr);
            node.setNoDataValue(Double.NaN);
            node.setNoDataValueUsed(true);
        }
    }


    private Product createRadiometricallyCorrectedProduct(Product product) {
        final HashMap<String, Object> radiometryParameters = new HashMap<>();
        radiometryParameters.put("doCalibration", false);
        radiometryParameters.put("doSmile", true);
        radiometryParameters.put("doEqualization", true);
        radiometryParameters.put("reproVersion", ReprocessingVersion.REPROCESSING_3);
        radiometryParameters.put("doRadToRefl", true);
        return GPF.createProduct("Meris.CorrectRadiometry", radiometryParameters, product);
    }

    private RenderedImage createFixedRangeUnmaskedRgbImage(Product product) {
        double minR = -2.0;
        double maxR = -1.0;

        double minG = -2.0;
        double maxG = -1.0;

        double minB = -1.5;
        double maxB = -0.5;

        double gamma = 1.2;

        Band r = product.getBand("vis_red");
        Band g = product.getBand("vis_green");
        Band b = product.getBand("vis_blue");

        RGBChannelDef rgbChannelDef = new RGBChannelDef(new String[]{r.getName(), g.getName(), b.getName()});
        rgbChannelDef.setMinDisplaySample(0, minR);
        rgbChannelDef.setMaxDisplaySample(0, maxR);
        rgbChannelDef.setGamma(0, gamma);

        rgbChannelDef.setMinDisplaySample(1, minG);
        rgbChannelDef.setMaxDisplaySample(1, maxG);
        rgbChannelDef.setGamma(1, gamma);

        rgbChannelDef.setMinDisplaySample(2, minB);
        rgbChannelDef.setMaxDisplaySample(2, maxB);
        rgbChannelDef.setGamma(2, gamma);

        return ImageManager.getInstance().createColoredBandImage(new Band[]{r, g, b}, new ImageInfo(rgbChannelDef), 0);
    }

    private RenderedImage createDynamicRangeMaskedRgbImage(Product product) {

        Band r = product.getBand("red");
        Band g = product.getBand("green");
        Band b = product.getBand("blue");

        Band[] bands = {r, g, b};
        for (Band band : bands) {
            band.getImageInfo(ProgressMonitor.NULL);
        }
        ImageInfo imageInfo = ImageManager.getInstance().getImageInfo(bands);
        return ImageManager.getInstance().createColoredBandImage(bands, imageInfo, 0);
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(BiTempSpectralFeatureWriter.class);
        }
    }

}
