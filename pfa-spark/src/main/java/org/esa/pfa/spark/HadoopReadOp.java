/*
 * Copyright (C) 2015 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.pfa.spark;

import com.bc.ceres.core.ProgressMonitor;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.TargetProduct;

import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.IOException;

/**
 * A GPF read OP, made for Calvalus.
 */
@OperatorMetadata(alias = "Read",
        version = "1.1",
        authors = "Marco Zuehlke, Norman Fomferra",
        copyright = "(c) 2014 by Brockmann Consult",
        description = "Reads a product from HDFS or from disk.")
public class HadoopReadOp extends Operator {

    @Parameter(description = "The file from which the data product is read.", notNull = true, notEmpty = true)
    private String file;
    @TargetProduct
    private Product targetProduct;

    private transient ProductReader productReader;


    @Override
    public void initialize() throws OperatorException {
        try {
            Path path = new Path(file);
            Configuration configuration = new Configuration();
            targetProduct = openProduct(path, configuration);
            productReader = targetProduct.getProductReader();
        } catch (IOException e) {
            throw new OperatorException(e);
        }
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        ProductData dataBuffer = targetTile.getRawSamples();
        Rectangle rectangle = targetTile.getRectangle();
        try {
            productReader.readBandRasterData(band, rectangle.x, rectangle.y, rectangle.width,
                                             rectangle.height, dataBuffer, pm);
            targetTile.setRawSamples(dataBuffer);
        } catch (IOException e) {
            throw new OperatorException(e);
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(HadoopReadOp.class);
        }
    }

    private static Product openProduct(Path path, Configuration conf) throws IOException {
        System.out.println("HadoopReadOp.openProduct path = [" + path + "], conf = [" + conf + "]");
        ImageInputStream imageInputStream = openImageInputStream(path, conf);
        Product product = readProductWithAutodetect(imageInputStream);
        System.out.println("product = " + product);
        return product;
    }

    private static ImageInputStream openImageInputStream(Path path, Configuration conf) throws IOException {
        FileSystem fs = path.getFileSystem(conf);
        FileStatus status = fs.getFileStatus(path);
        FSDataInputStream in = fs.open(path);
        return new FSImageInputStream(in, status.getLen());
    }

    private static Product readProductWithAutodetect(ImageInputStream input) throws IOException {
        ProductReader productReader = ProductIO.getProductReaderForInput(input);
        if (productReader != null) {
            return productReader.readProductNodes(input, null);
        }
        throw new IllegalArgumentException("Failed to find reader for product: ");
    }
}
