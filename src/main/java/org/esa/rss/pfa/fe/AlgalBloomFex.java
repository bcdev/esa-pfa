package org.esa.rss.pfa.fe;/*
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

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.Stx;
import org.esa.beam.framework.datamodel.StxFactory;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.gpf.operators.standard.SubsetOp;

import javax.media.jai.JAI;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;

public class AlgalBloomFex {

    private static final int TILE_WIDTH = 200;
    private static final int TILE_HEIGHT = 200;

    private static final int MiB = 1024 * 1024;

    static {
        System.setProperty("beam.reader.tileWidth", String.valueOf(TILE_WIDTH));
        System.setProperty("beam.reader.tileHeight", String.valueOf(TILE_HEIGHT));

        JAI.getDefaultInstance().getTileCache().setMemoryCapacity(1024 * MiB);
        JAI.getDefaultInstance().getTileScheduler().setParallelism(Runtime.getRuntime().availableProcessors());
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }


    void run(String[] args) throws IOException {
        if (args.length == 0) {
            printHelpMessage();
            System.exit(1);
        }
        final String path = args[0];
        final Product product = ProductIO.readProduct(path);
        if (product == null) {
            throw new IOException(MessageFormat.format("No reader found for product ''{0}''.", path));
        }

        final File fexDir = new File(product.getFileLocation().getPath() + ".fex");
        final boolean fexDirCreated = fexDir.mkdir();

        if (fexDirCreated) {
            final int width = product.getSceneRasterWidth();
            final int height = product.getSceneRasterHeight();
            final Rectangle boundary = new Rectangle(0, 0, width, height);

            final int tileCountX = (width + TILE_WIDTH - 1) / TILE_WIDTH;
            final int tileCountY = (height + TILE_HEIGHT - 1) / TILE_HEIGHT;
            // TODO - use

            for (int y = 0, tileY = 0; y < height; y += TILE_HEIGHT, tileY++) {
                for (int x = 0, tileX = 0; x < width; x += TILE_WIDTH, tileX++) {
                    final SubsetOp subsetOp = new SubsetOp();
                    final Rectangle region = new Rectangle(x, y, TILE_WIDTH, TILE_HEIGHT).intersection(boundary);
                    subsetOp.setRegion(region);
                    subsetOp.setSourceProduct(product);
                    final Product subsetProduct = subsetOp.getTargetProduct();

                    final File tileDir = new File(fexDir, String.format("x%dy%d", tileX, tileY));
                    final boolean tileDirCreated = tileDir.mkdir();

                    if (tileDirCreated) {
                        ProductIO.writeProduct(subsetProduct, new File(tileDir, "mer.dim"), "BEAM-DIMAP", false);
                        // TODO - ProductUtils.createRgbImage(new Band[], )

                        final StxFactory stxFactory = new StxFactory();

                        final String bandName = "radiance_13";
                        final Stx stx = stxFactory.create(subsetProduct.getBand(bandName),
                                                          ProgressMonitor.NULL);
                        final File featureFile = new File(tileDir, "features.txt");
                        final PrintWriter writer = new PrintWriter(featureFile);
                        writer.printf("%s.mean = %s\n", bandName, stx.getMean());
                        writer.printf("%s.stdev = %s\n", bandName, stx.getStandardDeviation());
                        writer.close();
                    } else {
                        // TODO - handle these cases
                    }
                }
            }
        } else {
            // TODO - handle these cases
        }
    }

    private void printHelpMessage() {

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
