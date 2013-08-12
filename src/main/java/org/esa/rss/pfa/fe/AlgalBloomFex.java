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
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.Stx;
import org.esa.beam.framework.datamodel.StxFactory;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.util.io.FileUtils;

import javax.media.jai.JAI;
import java.awt.Rectangle;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

public class AlgalBloomFex {

    private static final int MiB = 1024 * 1024;

    private static final int TILE_SIZE_X = 200;
    private static final int TILE_SIZE_Y = 200;

    private static final String FEX_EXTENSION = ".fex";

    static {
        System.setProperty("beam.reader.tileWidth", String.valueOf(TILE_SIZE_X));
        System.setProperty("beam.reader.tileHeight", String.valueOf(TILE_SIZE_Y));

        JAI.getDefaultInstance().getTileCache().setMemoryCapacity(1024 * MiB);
        JAI.getDefaultInstance().getTileScheduler().setParallelism(Runtime.getRuntime().availableProcessors());
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }

    void run(String[] args) throws IOException {
        if (args.length != 1) {
            printHelpMessage();
            System.exit(1);
        }
        final String path = args[0];
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

        final Product reflectanceProduct = createReflectanceProduct(sourceProduct);
        for (int tileY = 0; tileY < tileCountY; tileY++) {
            for (int tileX = 0; tileX < tileCountX; tileX++) {
                final File tileDir = new File(featureDir, String.format("x%dy%d", tileX, tileY));
                if (!tileDir.mkdir()) {
                    throw new IOException(MessageFormat.format("Tile directory ''{0}'' cannot be created.", tileDir));
                }

                final Product subsetProduct = createSubset(reflectanceProduct, tileY, tileX);
                writeFeatures(subsetProduct, tileDir);
                writeSubset(subsetProduct, tileDir);
                writeRgb(subsetProduct, tileDir);

                subsetProduct.closeIO();
            }
        }
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

    private Product createReflectanceProduct(Product sourceProduct) {
        final Map<String, Object> radiometryParameters = new HashMap<String, Object>();
        radiometryParameters.put("doCalibration", false);
        radiometryParameters.put("doSmile", true);
        radiometryParameters.put("doEqualization", true);
        radiometryParameters.put("doRadToRefl", true);

        return GPF.createProduct("Meris.CorrectRadiometry", radiometryParameters, sourceProduct);
    }

    private void writeRgb(Product subsetProduct, File tileDir) throws IOException {
        // TODO - ProductUtils.createRgbImage(new Band[], )
    }

    private void writeFeatures(Product subsetProduct, File tileDir) throws IOException {
        final StxFactory stxFactory = new StxFactory();
        final String bandName = "reflec_13";
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

    private void writeSubset(Product subsetProduct, File tileDir) throws IOException {
        final File subsetFile = new File(tileDir, "mer.dim");
        ProductIO.writeProduct(subsetProduct, subsetFile, "BEAM-DIMAP", false);
    }

    private void printHelpMessage() {
        System.out.println("Usage: " + getClass().getName() + " <product-file>");
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
