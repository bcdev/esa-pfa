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
import org.esa.beam.framework.datamodel.ImageInfo;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.RGBChannelDef;
import org.esa.beam.framework.datamodel.Stx;
import org.esa.beam.framework.datamodel.StxFactory;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.io.FileUtils;

import javax.imageio.ImageIO;
import javax.media.jai.JAI;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
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

    private static boolean skipFeaturesOutput = Boolean.getBoolean("skipFeatures");
    private static boolean skipRgbImageOutput = Boolean.getBoolean("skipRgbImage");
    private static boolean skipProductOutput = Boolean.getBoolean("skipProduct");

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
            extractFeatures(path);
        }
    }

    private void extractFeatures(String path) throws IOException {
        System.out.println("Reading " + path);

        final Product sourceProduct = ProductIO.readProduct(path);
        if (sourceProduct == null) {
            throw new IOException(MessageFormat.format("No reader found for product ''{0}''.", path));
        }
        final File sourceFile = sourceProduct.getFileLocation();
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

        final Product correctedProduct = createCorrectedProduct(sourceProduct);
        addMciBand(correctedProduct);

        for (int tileY = 0; tileY < tileCountY; tileY++) {
            for (int tileX = 0; tileX < tileCountX; tileX++) {
                final String tileDirName = String.format("x%02dy%02d", tileX, tileY);
                final File tileDir = new File(featureDir, tileDirName);
                if (!tileDir.mkdir()) {
                    throw new IOException(MessageFormat.format("Tile directory ''{0}'' cannot be created.", tileDir));
                }

                final Product subsetProduct = createSubset(correctedProduct, tileY, tileX);

                if (!skipFeaturesOutput) {
                    writeFeatures(subsetProduct, tileDir);
                }
                if (!skipProductOutput) {
                    writeProductSubset(subsetProduct, tileDir);
                }
                if (!skipRgbImageOutput) {
                    writeRgbImages(subsetProduct, tileDir);
                }

                subsetProduct.dispose();
            }
        }
    }

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
        radiometryParameters.put("doRadToRefl", false);

        return GPF.createProduct("Meris.CorrectRadiometry", radiometryParameters, sourceProduct);
    }

    private void writeRgbImages(Product product, File tileDir) throws IOException {
        writeReflectanceRgbImage(product, tileDir);
        writeRadianceRgbImage(product, tileDir);
    }

    private void writeReflectanceRgbImage(Product subsetProduct, File tileDir) throws IOException {
        final HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("doSmile", false);
        parameters.put("doCalibration", false);
        parameters.put("doEqualization", false);
        parameters.put("doRadToRefl", true);
        final Product product = GPF.createProduct("Meris.CorrectRadiometry", parameters, subsetProduct);

        writeImage(product,
                   "log(0.05 + 0.35 * reflec_2 + 0.60 * reflec_5 + reflec_6 + 0.13 * reflec_7)",
                   "log(0.05 + 0.21 * reflec_3 + 0.50 * reflec_4 + reflec_5 + 0.38 * reflec_6)",
                   "log(0.05 + 0.21 * reflec_1 + 1.75 * reflec_2 + 0.47 * reflec_3 + 0.16 * reflec_4)",
                   -1.95, -1.35, 1.1,
                   -1.9, -1.4, 1.1,
                   -1.3, -0.7, 1.0,
                   new File(tileDir.getParentFile(), tileDir.getName() + "_ref.png"));
    }

    private void writeRadianceRgbImage(Product product, File tileDir) throws IOException {
        writeImage(product,
                   "log(1.0 + 0.35 * radiance_2 + 0.60 * radiance_5 + radiance_6 + 0.13 * radiance_7)",
                   "log(1.0 + 0.21 * radiance_3 + 0.50 * radiance_4 + radiance_5 + 0.38 * radiance_6)",
                   "log(1.0 + 0.21 * radiance_1 + 1.75 * radiance_2 + 0.47 * radiance_3 + 0.16 * radiance_4)",
                   3.6, 4.5, 1.1,
                   3.7, 4.3, 1.1,
                   4.6, 5.0, 1.0,
                   new File(tileDir.getParentFile(), tileDir.getName() + "_rad.png"));
    }

    private void writeImage(Product product,
                            String expressionR,
                            String expressionG,
                            String expressionB,
                            double minR, double maxR, double gammaR,
                            double minG, double maxG, double gammaG,
                            double minB, double maxB, double gammaB,
                            File outputFile) throws IOException {
        final Band r = product.addBand("red", expressionR);
        final Band g = product.addBand("green", expressionG);
        final Band b = product.addBand("blue", expressionB);

        final RGBChannelDef rgbChannelDef = new RGBChannelDef(new String[]{"red", "green", "blue"});
        rgbChannelDef.setMinDisplaySample(0, minR);
        rgbChannelDef.setMaxDisplaySample(0, maxR);
        rgbChannelDef.setGamma(0, gammaR);

        rgbChannelDef.setMinDisplaySample(1, minG);
        rgbChannelDef.setMaxDisplaySample(1, maxG);
        rgbChannelDef.setGamma(0, gammaG);

        rgbChannelDef.setMinDisplaySample(2, minB);
        rgbChannelDef.setMaxDisplaySample(2, maxB);
        rgbChannelDef.setGamma(0, gammaB);

        BufferedImage rgbImage = ProductUtils.createRgbImage(new Band[]{r, g, b}, new ImageInfo(rgbChannelDef), ProgressMonitor.NULL);
        ImageIO.write(rgbImage, "PNG", outputFile);
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
