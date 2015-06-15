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

package org.esa.pfa.fe;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glevel.support.AbstractMultiLevelSource;
import com.bc.ceres.glevel.support.DefaultMultiLevelImage;
import org.esa.pfa.fe.op.DatasetDescriptor;
import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.FeatureWriter;
import org.esa.pfa.fe.op.FeatureWriterResult;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.fe.op.out.PatchSink;
import org.esa.s3tbx.meris.radiometry.equalization.ReprocessingVersion;
import org.esa.snap.framework.dataio.ProductIO;
import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.ConvolutionFilterBand;
import org.esa.snap.framework.datamodel.ImageInfo;
import org.esa.snap.framework.datamodel.Kernel;
import org.esa.snap.framework.datamodel.Mask;
import org.esa.snap.framework.datamodel.MetadataAttribute;
import org.esa.snap.framework.datamodel.MetadataElement;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.ProductData;
import org.esa.snap.framework.datamodel.RGBChannelDef;
import org.esa.snap.framework.datamodel.RasterDataNode;
import org.esa.snap.framework.datamodel.Stx;
import org.esa.snap.framework.datamodel.StxFactory;
import org.esa.snap.framework.gpf.GPF;
import org.esa.snap.framework.gpf.OperatorException;
import org.esa.snap.framework.gpf.OperatorSpi;
import org.esa.snap.framework.gpf.annotations.OperatorMetadata;
import org.esa.snap.framework.gpf.annotations.Parameter;
import org.esa.snap.framework.gpf.graph.Graph;
import org.esa.snap.framework.gpf.graph.GraphContext;
import org.esa.snap.framework.gpf.graph.GraphException;
import org.esa.snap.framework.gpf.graph.GraphIO;
import org.esa.snap.framework.gpf.graph.GraphProcessor;
import org.esa.snap.jai.ImageManager;
import org.esa.snap.jai.ResolutionLevel;
import org.esa.snap.runtime.Config;
import org.esa.snap.util.ProductUtils;
import org.esa.snap.util.ResourceInstaller;
import org.esa.snap.util.SystemUtils;

import java.awt.*;
import java.awt.image.DataBufferFloat;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * An operator for extracting algal bloom features.
 *
 * @author Norman Fomferra
 * @author Ralf Quast
 */
@OperatorMetadata(alias = "AlgalBloomFeatureWriter", version = "1.1", autoWriteDisabled = true,
        category = "Image Analysis/Feature Extraction")
public class AlgalBloomFeatureWriter extends FeatureWriter {

    public static final int DEFAULT_PATCH_SIZE = 200;

    public static final File AUXDATA_DIR = SystemUtils.getAuxDataPath().resolve("pfa-algalblooms").toFile();

    String OC4_R = "log10(max(max(reflec_2, reflec_3), reflec_4) / reflec_5)";
    String OC4_CHL = "exp10(0.366 - 3.067*R + 1.930*pow(R,2) + 0.649 *pow(R,3)  - 1.532 *pow(R,4))";

    private double minSampleFlh;
    private double maxSampleFlh;
    private double minSampleMci;
    private double maxSampleMci;
    private double minSampleChl;
    private double maxSampleChl;

    public static void main(String[] args) {
        String sourcePath = args[0];
        String targetDirPath = args[1];

        System.setProperty("snap.dataio.reader.tileWidth", String.valueOf(DEFAULT_PATCH_SIZE));
        System.setProperty("snap.dataio.reader.tileHeight", String.valueOf(DEFAULT_PATCH_SIZE));
        System.setProperty("snap.parallelism", "1");

        Config.instance().load();

        SystemUtils.init3rdPartyLibs(AlgalBloomFeatureWriter.class);
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
        PFAApplicationDescriptor applicationDescriptor = new AlgalBloomApplicationDescriptor();
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
        /*
        TODO - ask Norman what the hell this is
        Node fexOpNode = graph.getNode("fexOp");
        NodeContext fexOpNodeCtx = graphContext.getNodeContext(fexOpNode);
        Operator fexOp = fexOpNodeCtx.getOperator();
        Map<Patch, Feature[]> fexFeatures = (Map<Patch, Feature[]>) fexOp.getTargetProperty("fexFeatures");
        if (fexFeatures != null) {
            // emit sourcePath + Patch --> Feature[]
        }
        */
        graphContext.dispose();
    }


    public static final String FEX_VALID_MASK = "NOT (l1_flags.INVALID OR l1_flags.LAND_OCEAN)";

    public static final String FEX_CLOUD_MASK_1_NAME = "fex_cloud_1";
    public static final String FEX_CLOUD_MASK_1_VALUE = "l1_flags.BRIGHT";

    public static final String FEX_CLOUD_MASK_2_NAME = "fex_cloud_2";
    public static final String FEX_CLOUD_MASK_2_VALUE = "cl_wat_3_val > 1.8";

    private static final String FEX_CLOUD_MASK_3_NAME = "cloud_mask";

    public static final String FEX_ROI_MASK_NAME = "fex_roi";

    public static final String FEX_COAST_DIST_PRODUCT_FILE = "coast_dist_2880.dim";

    @Parameter(defaultValue = "0.2")
    private double minValidPixelRatio;
    @Parameter(defaultValue = "0.0")
    private double minClumpiness;
    @Parameter(defaultValue = "1.005", description = "Cloud correction factor for MCI/FLH computation")
    private double cloudCorrectionFactor;
    @Parameter(defaultValue = "8", description = "Number of successful cloudiness tests for Fronts cloud mask")
    private int frontsCloudMaskThreshold;
    @Parameter(defaultValue = "0.00005",
            description = "Threshold for counting pixels whose absolute spatial FLH gradient is higher than the threshold")
    private double flhGradientThreshold;


    private transient float[] coastDistData;
    private transient int coastDistWidth;
    private transient int coastDistHeight;

    private FeatureType[] featureTypes;

    @Override
    protected FeatureType[] getFeatureTypes() {

        if (featureTypes == null) {
            featureTypes = new AlgalBloomApplicationDescriptor().getFeatureTypes();
        }
        return featureTypes;
    }

    @Override
    public void initialize() throws OperatorException {

        writeDatasetDescriptor();

        installAuxiliaryData(AUXDATA_DIR.toPath());

        minSampleFlh = 0.0;
        maxSampleFlh = 0.0025;

        minSampleMci = -0.004;
        maxSampleMci = 0.0;

        minSampleChl = 0.0;
        maxSampleChl = 0.75;

        Product coastDistProduct;
        try {
            coastDistProduct = ProductIO.readProduct(new File(AUXDATA_DIR, FEX_COAST_DIST_PRODUCT_FILE));
        } catch (IOException e) {
            throw new OperatorException(e);
        }

        // todo - regenerate a better costDist dataset. The current one has a cutoff at ~800 nm
        final Band coastDistance = coastDistProduct.addBand("coast_dist_nm_cleaned",
                                                            "coast_dist_nm > 300.0 ? 300.0 : coast_dist_nm");
        coastDistWidth = coastDistProduct.getSceneRasterWidth();
        coastDistHeight = coastDistProduct.getSceneRasterHeight();
        coastDistData = ((DataBufferFloat) coastDistance.getSourceImage().getData().getDataBuffer()).getData();
        coastDistProduct.dispose();


        patchWriterConfig = new HashMap<>();
        patchWriterConfig.put("html.labelValues", new String[][]{
                        /*0*/ {"ab_none", "* Not a Bloom *"},
                        /*1*/ {"ab_cyano", "Cyanobacteria"},
                        /*2*/ {"ab_coco", "Cocolithophores"},
                        /*3*/ {"ab_float", "Floating Bloom"},
                        /*4*/ {"ab_case_1", "Case 1 Bloom"},
                        /*5*/ {"ab_coastal", "Coastal Bloom"},
                        /*6*/ {"ab_susp_mat", "Suspended Matter"},
        });

        super.initialize();

        result = new FeatureWriterResult(getSourceProduct().getName());

    }

    private void writeDatasetDescriptor() {
        File parentFile = getTargetDir().getAbsoluteFile().getParentFile();
        File file = new File(parentFile, "ds-descriptor.xml");
        if (!file.exists()) {
            AlgalBloomApplicationDescriptor applicationDescriptor = new AlgalBloomApplicationDescriptor();
            DatasetDescriptor datasetDescriptor = new DatasetDescriptor(applicationDescriptor.getName(), "1.0", "Algal Bloom Detection", applicationDescriptor.getFeatureTypes());
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

        final Product featureProduct = createRadiometricallyCorrectedProduct(patchProduct);
        final Product wasteProduct = addMasks(featureProduct);
        final Mask roiMask = featureProduct.getMaskGroup().get(FEX_ROI_MASK_NAME);
        final ConnectivityMetrics connectivityMetrics = ConnectivityMetrics.compute(roiMask);

        final double validPixelRatio = connectivityMetrics.insideCount / (double) numPixelsRequired;
        if (validPixelRatio <= minValidPixelRatio) {
            getLogger().warning(String.format("Rejected patch x%dy%d, validPixelRatio = %f%%", patchX, patchY,
                                              validPixelRatio * 100));
            disposeProducts(featureProduct, wasteProduct);
            return false;
        }

        final AggregationMetrics aggregationMetrics = AggregationMetrics.compute(roiMask);
        final double clumpiness = aggregationMetrics.clumpiness;
        if (validPixelRatio < 0.5 && clumpiness < minClumpiness) {
            getLogger().warning(String.format("Rejected patch x%dy%d, clumpiness = %f", patchX, patchY, clumpiness));
            disposeProducts(featureProduct, wasteProduct);
            return false;
        }

        addMciBand(featureProduct);
        addFlhBand(featureProduct);
        addChlBand(featureProduct);
        addCoastDistBand(featureProduct);
        addTriStimulusBands(featureProduct);
        addFlhGradientBands(featureProduct);

        List<Feature> features = new ArrayList<>();

        if (!skipProductOutput) {
            features.add(new Feature(featureTypes[0], featureProduct));
        }

        if (!skipQuicklookOutput) {
            features.add(new Feature(featureTypes[1],
                                     createFixedRangeUnmaskedRgbImage(featureProduct)));
            features.add(new Feature(featureTypes[2],
                                     createDynamicRangeMaskedRgbImage(featureProduct)));
            features.add(new Feature(featureTypes[3],
                                     createColoredBandImage(featureProduct.getBand("flh"), minSampleFlh, maxSampleFlh)));
            features.add(new Feature(featureTypes[4],
                                     createColoredBandImage(featureProduct.getBand("mci"), minSampleMci, maxSampleMci)));
            features.add(new Feature(featureTypes[5],
                                     createColoredBandImage(featureProduct.getBand("chl"), minSampleChl, maxSampleChl)));
        }

        features.add(createStxFeature(featureTypes[6], featureProduct));
        features.add(createStxFeature(featureTypes[7], featureProduct));
        features.add(createStxFeature(featureTypes[8], featureProduct));
        features.add(createStxFeature(featureTypes[9], featureProduct));
        features.add(createStxFeature(featureTypes[10], featureProduct));
        features.add(createStxFeature(featureTypes[11], featureProduct));
        features.add(createStxFeature(featureTypes[12], featureProduct));
        features.add(new Feature(featureTypes[13], computeFlhHighGradientPixelRatio(featureProduct)));
        features.add(new Feature(featureTypes[14], validPixelRatio));
        features.add(new Feature(featureTypes[15], connectivityMetrics.fractalIndex));
        features.add(new Feature(featureTypes[16], clumpiness));

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

    private double computeFlhHighGradientPixelRatio(Product featureProduct) {
        StxFactory stxFactory = new StxFactory();
        stxFactory.withRoiMask(featureProduct.getMaskGroup().get("flh_high_gradient"));
        Stx stx = stxFactory.create(featureProduct.getBand("flh"), ProgressMonitor.NULL);
        double maxRatio = 0.5;
        double value = (double) stx.getSampleCount() / (maxRatio * patchWidth * patchHeight);
        return value >= 1.0 ? 1.0 : value;
    }

    private void addFlhGradientBands(Product featureProduct) {
        featureProduct.addBand(new ConvolutionFilterBand("flh_average", featureProduct.getBand("flh"),
                                                         new Kernel(3, 3, 1.0 / 9.0,
                                                                    new double[]{1, 1, 1, 1, 1, 1, 1, 1, 1}), 1
        ));
        featureProduct.addBand(new ConvolutionFilterBand("flh_gradient", featureProduct.getBand("flh_average"),
                                                         new Kernel(3, 3, 1.0 / 9.0,
                                                                    new double[]{-1, -2, -1, 0, 0, 0, 1, 2, 1}), 1
        ));
        featureProduct.addMask("flh_high_gradient", "abs(flh_gradient) > " + flhGradientThreshold, "", Color.RED, 0.5);
    }

    private void installAuxiliaryData(Path targetPath) {
        try {
            Path basePath = ResourceInstaller.findModuleCodeBasePath(this.getClass());
            Path auxdata = basePath.resolve("auxdata");

            final ResourceInstaller installer = new ResourceInstaller(auxdata, targetPath);
            installer.install(".*", ProgressMonitor.NULL);
        } catch (IOException e) {
            throw new OperatorException(e);
        }
    }

    /*
     * @return intermediate waste product for later disposal.
     */
    private Product addMasks(Product product) {
        final Product cloudProduct = createFrontsCloudMaskProduct(product);
        ProductUtils.copyBand("cloud_data_ori_or_flag", cloudProduct, product, true);
        ProductUtils.copyMasks(cloudProduct, product);
        addRoiMask(product, FEX_CLOUD_MASK_3_NAME);
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
        op.setThreshold(frontsCloudMaskThreshold);
        return op.getTargetProduct();
    }

    private Band addMciBand(Product product) {
        final Band l1 = product.getBand("reflec_8");
        final Band l2 = product.getBand("reflec_9");
        final Band l3 = product.getBand("reflec_10");

        final double lambda1 = l1.getSpectralWavelength();
        final double lambda2 = l2.getSpectralWavelength();
        final double lambda3 = l3.getSpectralWavelength();
        final double factor = (lambda2 - lambda1) / (lambda3 - lambda1);

        final Band mci = product.addBand("mci",
                                         String.format("%s - %s * (%s + (%s - %s) * %s)",
                                                       l2.getName(),
                                                       cloudCorrectionFactor,
                                                       l1.getName(),
                                                       l3.getName(),
                                                       l1.getName(), factor)
        );
        mci.setValidPixelExpression(FEX_ROI_MASK_NAME);
        return mci;
    }

    private void addFlhBand(Product product) {
        final Band l1 = product.getBand("reflec_7");
        final Band l2 = product.getBand("reflec_8");
        final Band l3 = product.getBand("reflec_9");

        final double lambda1 = l1.getSpectralWavelength();
        final double lambda2 = l2.getSpectralWavelength();
        final double lambda3 = l3.getSpectralWavelength();
        final double factor = (lambda2 - lambda1) / (lambda3 - lambda1);

        final Band flh = product.addBand("flh",
                                         String.format("%s - %s * (%s + (%s - %s) * %s)",
                                                       l2.getName(),
                                                       cloudCorrectionFactor,
                                                       l1.getName(),
                                                       l3.getName(),
                                                       l1.getName(), factor)
        );
        flh.setValidPixelExpression(FEX_ROI_MASK_NAME);
    }

    private void addChlBand(Product product) {
        Band R = product.addBand("R", OC4_R);
        Band chl = product.addBand("chl", OC4_CHL);
        applyValidPixelExpr(FEX_ROI_MASK_NAME, chl);
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

    private void addCoastDistBand(final Product product) {
        final Band coastDistBand = product.addBand("coast_dist", ProductData.TYPE_FLOAT32);
        final DefaultMultiLevelImage coastDistImage = new DefaultMultiLevelImage(
                new AbstractMultiLevelSource(ImageManager.getMultiLevelModel(coastDistBand)) {
                    @Override
                    protected RenderedImage createImage(int level) {
                        return new WorldDataOpImage(product.getGeoCoding(), coastDistBand,
                                                    ResolutionLevel.create(getModel(), level),
                                                    coastDistWidth, coastDistHeight, coastDistData);
                    }
                }
        );
        coastDistBand.setSourceImage(coastDistImage);
        coastDistBand.setValidPixelExpression(FEX_ROI_MASK_NAME);
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

    private static String[] appendArgs(String operatorName, String[] args) {
        List<String> algalBloomFex = new ArrayList<>(Arrays.asList(operatorName));
        algalBloomFex.addAll(Arrays.asList(args));
        return algalBloomFex.toArray(new String[algalBloomFex.size()]);
    }

    private static void removeAllMetadata(Product product) {
        MetadataElement metadataRoot = product.getMetadataRoot();
        MetadataElement[] elements = metadataRoot.getElements();
        for (MetadataElement element : elements) {
            metadataRoot.removeElement(element);
        }
        MetadataAttribute[] attributes = metadataRoot.getAttributes();
        for (MetadataAttribute attribute : attributes) {
            metadataRoot.removeAttribute(attribute);
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AlgalBloomFeatureWriter.class);
        }
    }

}
