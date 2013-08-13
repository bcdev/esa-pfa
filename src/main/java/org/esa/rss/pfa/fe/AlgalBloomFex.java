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

package org.esa.rss.pfa.fe;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.ImageInfo;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.RGBChannelDef;
import org.esa.beam.framework.datamodel.Stx;
import org.esa.beam.framework.datamodel.StxFactory;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.jai.ImageManager;
import org.esa.beam.meris.radiometry.equalization.ReprocessingVersion;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.io.FileUtils;

import javax.media.jai.JAI;
import javax.media.jai.operator.FileStoreDescriptor;
import java.awt.Rectangle;
import java.awt.image.RenderedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.HashMap;

public class AlgalBloomFex {

    private static final int MiB = 1024 * 1024;

    private static final int TILE_SIZE_X = 200;
    private static final int TILE_SIZE_Y = 200;

    private static final String FEX_EXTENSION = ".fex";
    public static final String FEX_VALID_MASK = "NOT (l1_flags.INVALID OR l1_flags.LAND_OCEAN OR l1_flags.BRIGHT OR l1_flags.GLINT_RISK)";
    public static final String FEX_L1B_CLOUD_MASK = "distance(radiance_2/radiance_1,radiance_3/radiance_1,radiance_4/radiance_1,radiance_5/radiance_1,radiance_6/radiance_1,radiance_7/radiance_1,radiance_8/radiance_1,radiance_9/radiance_1,radiance_10/radiance_1,radiance_11/radiance_1,radiance_12/radiance_1,radiance_13/radiance_1,radiance_14/radiance_1,radiance_15/radiance_1," +
            "1.0744720212301275,1.0733119255986385,1.0479161563791768,0.9315456603467167,0.8480901646693009,0.8288787653690769,0.8071113370747969,0.764724290638019,0.7128622108550259,0.23310952131660026,0.666867538685338,0.5517312317788085,0.5319259202911271,0.4004727059350037)/15 < 0.095";

    private static boolean skipFeaturesOutput = Boolean.getBoolean("skipFeatures");
    private static boolean skipRgbImageOutput = Boolean.getBoolean("skipRgbImage");
    private static boolean skipProductOutput = Boolean.getBoolean("skipProduct");
    private static boolean equirectangular = Boolean.getBoolean("equirectangular");

    static {
        System.setProperty("beam.reader.tileWidth", String.valueOf(TILE_SIZE_X));
        System.setProperty("beam.reader.tileHeight", String.valueOf(TILE_SIZE_Y));

        JAI.getDefaultInstance().getTileCache().setMemoryCapacity(1024 * MiB);
        JAI.getDefaultInstance().getTileScheduler().setParallelism(Runtime.getRuntime().availableProcessors());
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }

    void run(String[] args) throws IOException {
        if (args.length == 0) {
            printHelpMessage();
            System.exit(1);
        }

        if (skipFeaturesOutput) {
            System.out.println("Warning: Feature output skipped.");
        }
        if (skipProductOutput) {
            System.out.println("Warning: Product output skipped.");
        }
        if (skipRgbImageOutput) {
            System.out.println("Warning: RGB image output skipped.");
        }

        for (String path : args) {
            extractFeatures(new File(path));
        }
    }

    private void extractFeatures(File sourceFile) throws IOException {
        System.out.println("Reading " + sourceFile);

        final Product sourceProduct = ProductIO.readProduct(sourceFile);
        if (sourceProduct == null) {
            throw new IOException(MessageFormat.format("No reader found for product ''{0}''.", sourceFile.getPath()));
        }
        final File featureDir = new File(sourceFile.getPath() + FEX_EXTENSION);
        if (featureDir.exists()) {
            if (!FileUtils.deleteTree(featureDir)) {
                throw new IOException(
                        MessageFormat.format("Existing feature directory ''{0}'' cannot be deleted.", featureDir));
            }
        }
        if (!featureDir.mkdir()) {
            throw new IOException(MessageFormat.format("Feature directory ''{0}'' cannot be created.", featureDir));
        }

        final int productSizeX = sourceProduct.getSceneRasterWidth();
        final int productSizeY = sourceProduct.getSceneRasterHeight();
        final int tileCountX = (productSizeX + TILE_SIZE_X - 1) / TILE_SIZE_X;
        final int tileCountY = (productSizeY + TILE_SIZE_Y - 1) / TILE_SIZE_Y;

        Kml kml = null;
        final Product correctedProduct = createCorrectedProduct(sourceProduct);
        addMciBand(correctedProduct);
        //addCloudMask(correctedProduct);

        KmlWriter kmlWriter = null;

        for (int tileY = 0; tileY < tileCountY; tileY++) {
            for (int tileX = 0; tileX < tileCountX; tileX++) {
                final String tileDirName = String.format("x%02dy%02d", tileX, tileY);
                final File tileDir = new File(featureDir, tileDirName);
                if (!tileDir.mkdir()) {
                    throw new IOException(MessageFormat.format("Tile directory ''{0}'' cannot be created.", tileDir));
                }

                final Product subsetProduct = createSubset(sourceProduct, tileY, tileX);
                final Product correctedProduct = createCorrectedProduct(subsetProduct);
                addMciBand(correctedProduct);

                final Product waterProduct = createReflectanceProduct(correctedProduct);
                for (final String bandName : waterProduct.getBandNames()) {
                    if (bandName.startsWith("reflec")) {
                        ProductUtils.copyBand(bandName, waterProduct, correctedProduct, true);
                    }
                }
                addFlhBand(correctedProduct);

                if (!skipFeaturesOutput) {
                    writeFeatures(correctedProduct, tileDir);
                }
                if (!skipProductOutput) {
                    writeProductSubset(correctedProduct, tileDir);
                }
                if (!skipRgbImageOutput) {
                    if (kmlWriter == null) {
                        Writer writer = new FileWriter(new File(featureDir, "overview.kml"));
                        kmlWriter = new KmlWriter(writer, sourceFile.getName(), "RGB tiles from reflectances of " + sourceFile.getName());
                    }
                    writeRgbImages(subsetProduct, tileDir, kmlWriter);
                }

                waterProduct.dispose();
                correctedProduct.dispose();
                subsetProduct.dispose();
            }
        }

        if (kmlWriter != null) {
            kmlWriter.close();
        }
    }

    private Product createReflectanceProduct(Product sourceProduct) {
        final HashMap<String, Object> radiometryParameters = new HashMap<String, Object>();
        radiometryParameters.put("doCalibration", false);
        radiometryParameters.put("doSmile", false);
        radiometryParameters.put("doEqualization", false);
        radiometryParameters.put("doRadToRefl", true);

        return GPF.createProduct("Meris.CorrectRadiometry", radiometryParameters, sourceProduct);
    }

    private void addFlhBand(Product sourceProduct) {
        final Band l1 = sourceProduct.getBand("reflec_7");
        final Band l2 = sourceProduct.getBand("reflec_8");
        final Band l3 = sourceProduct.getBand("reflec_9");

        final double lambda1 = l1.getSpectralWavelength();
        final double lambda2 = l2.getSpectralWavelength();
        final double lambda3 = l3.getSpectralWavelength();
        final double factor = (lambda2 - lambda1) / (lambda3 - lambda1);
        final double cloudCorrectionFactor = 1.005;

        sourceProduct.addBand("flh",
                              String.format("%s - %s * (%s - (%s - %s) * %s)",
                                            l2.getName(),
                                            cloudCorrectionFactor,
                                            l1.getName(),
                                            l3.getName(),
                                            l1.getName(), factor));
    }

    /*
    private void addCloudMask(Product product) {
         product.addMask("fex_cloud_mask", FEX_L1B_CLOUD_MASK, "Special MERIS L1B 'cloud' mask for PFA", Color.YELLOW, 0.5);
    }
    */

    private void addMciBand(Product sourceProduct) {
        final Band l1 = sourceProduct.getBand("radiance_8");
        final Band l2 = sourceProduct.getBand("radiance_9");
        final Band l3 = sourceProduct.getBand("radiance_10");

        final double lambda1 = l1.getSpectralWavelength();
        final double lambda2 = l2.getSpectralWavelength();
        final double lambda3 = l3.getSpectralWavelength();
        final double factor = (lambda2 - lambda1) / (lambda3 - lambda1);
        final double cloudCorrectionFactor = 1.005;

        sourceProduct.addBand("mci",
                              String.format("%s - %s * (%s - (%s - %s) * %s)",
                                            l2.getName(),
                                            cloudCorrectionFactor,
                                            l1.getName(),
                                            l3.getName(),
                                            l1.getName(), factor));
    }

    private Product createSubset(Product sourceProduct, int tileY, int tileX) {
        final int productSizeX = sourceProduct.getSceneRasterWidth();
        final int productSizeY = sourceProduct.getSceneRasterHeight();
        final Rectangle sceneBoundary = new Rectangle(0, 0, productSizeX, productSizeY);
        final int x = tileX * TILE_SIZE_X;
        final int y = tileY * TILE_SIZE_Y;
        final Rectangle subsetRegion = new Rectangle(x, y, TILE_SIZE_X, TILE_SIZE_Y).intersection(sceneBoundary);

        final HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("region", subsetRegion);

        return GPF.createProduct("Subset", parameters, sourceProduct);
    }

    private Product createCorrectedProduct(Product sourceProduct) {
        final HashMap<String, Object> radiometryParameters = new HashMap<String, Object>();
        radiometryParameters.put("doCalibration", false);
        radiometryParameters.put("doSmile", true);
        radiometryParameters.put("doEqualization", true);
        radiometryParameters.put("reproVersion", ReprocessingVersion.REPROCESSING_3);
        radiometryParameters.put("doRadToRefl", false);

        return GPF.createProduct("Meris.CorrectRadiometry", radiometryParameters, sourceProduct);
    }

    private void writeRgbImages(Product product, File tileDir, KmlWriter kmlWriter) throws IOException {

        if (equirectangular) {
            HashMap<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("crs", "EPSG:4326");
            parameters.put("resamplingName", "Bicubic");
            product = GPF.createProduct("Reproject", parameters, product);
        }

        float w = product.getSceneRasterWidth();
        float h = product.getSceneRasterHeight();

        String rgbBaseName = tileDir.getName() + "_rgb";
        String rgbFileName = rgbBaseName + ".png";
        if (equirectangular) {
            GeoPos geoPosUL = product.getGeoCoding().getGeoPos(new PixelPos(0, 0), null);
            GeoPos geoPosLR = product.getGeoCoding().getGeoPos(new PixelPos(w, h), null);
            kmlWriter.writeGroundOverlay(rgbBaseName, geoPosUL, geoPosLR, rgbFileName);
        } else {
            // quadPositions: counter clockwise lon,lat coordinates starting at lower-left
            GeoPos[] quadPositions = new GeoPos[]{
                    product.getGeoCoding().getGeoPos(new PixelPos(0, h), null),
                    product.getGeoCoding().getGeoPos(new PixelPos(w, h), null),
                    product.getGeoCoding().getGeoPos(new PixelPos(w, 0), null),
                    product.getGeoCoding().getGeoPos(new PixelPos(0, 0), null),
            };
            kmlWriter.writeGroundOverlayEx(rgbBaseName, quadPositions, rgbFileName);
        }
        writeReflectanceRgbImage(product, new File(tileDir.getParentFile(), rgbFileName));
    }

    private void writeReflectanceRgbImage(Product subsetProduct, File outputFile) throws IOException {
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("doSmile", false);
        parameters.put("doCalibration", false);
        parameters.put("doEqualization", false);
        parameters.put("doRadToRefl", true);
        Product product = GPF.createProduct("Meris.CorrectRadiometry", parameters, subsetProduct);

        writeImage(product,
                   "log(0.05 + 0.35 * reflec_2 + 0.60 * reflec_5 + reflec_6 + 0.13 * reflec_7)",
                   "log(0.05 + 0.21 * reflec_3 + 0.50 * reflec_4 + reflec_5 + 0.38 * reflec_6)",
                   "log(0.05 + 0.21 * reflec_1 + 1.75 * reflec_2 + 0.47 * reflec_3 + 0.16 * reflec_4)",
                   // FEX_VALID_MASK + " && !fex_cloud_mask",
                   FEX_VALID_MASK,
                   -1.95, -1.35, 1.1,
                   -1.9, -1.4, 1.1,
                   -1.3, -0.7, 1.0,
                   outputFile);
    }

    private void writeImage(Product product,
                            String expressionR,
                            String expressionG,
                            String expressionB,
                            String expressionA,
                            double minR, double maxR, double gammaR,
                            double minG, double maxG, double gammaG,
                            double minB, double maxB, double gammaB,
                            File outputFile) throws IOException {
        Band r = product.addBand("fexRed", expressionR);
        Band g = product.addBand("fexGreen", expressionG);
        Band b = product.addBand("fexBlue", expressionB);

        r.setValidPixelExpression(expressionA);
        r.setNoDataValue(Double.NaN);
        r.setNoDataValueUsed(true);

        RGBChannelDef rgbChannelDef = new RGBChannelDef(new String[]{"fexRed", "fexGreen", "fexBlue"});
        rgbChannelDef.setMinDisplaySample(0, minR);
        rgbChannelDef.setMaxDisplaySample(0, maxR);
        rgbChannelDef.setGamma(0, gammaR);

        rgbChannelDef.setMinDisplaySample(1, minG);
        rgbChannelDef.setMaxDisplaySample(1, maxG);
        rgbChannelDef.setGamma(1, gammaG);

        rgbChannelDef.setMinDisplaySample(2, minB);
        rgbChannelDef.setMaxDisplaySample(2, maxB);
        rgbChannelDef.setGamma(2, gammaB);

        RenderedImage rgbaImage = ImageManager.getInstance().createColoredBandImage(new Band[]{r, g, b}, new ImageInfo(rgbChannelDef), 0);
        FileStoreDescriptor.create(rgbaImage, outputFile.getPath(), "PNG", null, null, null);

        product.removeBand(r);
        product.removeBand(g);
        product.removeBand(b);
    }

    private void writeFeatures(Product subsetProduct, File tileDir) throws IOException {
        final StxFactory stxFactory = new StxFactory();
        final String bandName = "mci";
        final Stx stx = stxFactory.create(subsetProduct.getBand(bandName), ProgressMonitor.NULL);
        final File featureFile = new File(tileDir, "features.txt");
        final Writer featureWriter = new BufferedWriter(new FileWriter(featureFile));
        try {
            featureWriter.write(String.format("%s.mean = %s\n", bandName, stx.getMean()));
            featureWriter.write(String.format("%s.stdev = %s\n", bandName, stx.getStandardDeviation()));
        } finally {
            try {
                featureWriter.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void writeProductSubset(Product subsetProduct, File tileDir) throws IOException {
        final File subsetFile = new File(tileDir, "mer.dim");
        ProductIO.writeProduct(subsetProduct, subsetFile, "BEAM-DIMAP", false);
    }

    private void printHelpMessage() {
        System.out.println("Usage: " + getClass().getName() + " <product-file-1> <product-file-2> <product-file-3> ...");
    }

    public static void main(String[] args) {
        try {
            new AlgalBloomFex().run(args);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

}
