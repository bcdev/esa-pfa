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

package org.esa.rss.pfa.fe.op;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.experimental.Output;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.HashMap;

/**
 * Abstract feature extraction operator. Features are extracted from product "patches".
 *
 * @author Norman Fomferra
 */
@OperatorMetadata(alias = "FexOp", version = "1.0")
public abstract class FexOperator extends Operator implements Output {

    @Parameter(description = "The path where features will be extracted to")
    protected String targetPath;

    @Parameter(defaultValue = "200")
    protected int patchWidth;
    @Parameter(defaultValue = "200")
    protected int patchHeight;

    @Parameter(defaultValue = "false")
    protected boolean skipFeaturesOutput;
    @Parameter(defaultValue = "false")
    protected boolean skipRgbImageOutput;
    @Parameter(defaultValue = "false")
    protected boolean skipProductOutput;

    @Parameter(defaultValue = "org.esa.rss.pfa.fe.op.DefaultFeatureOutputFactory")
    private String featureOutputFactoryClassName;

    private transient FeatureOutputFactory featureOutputFactory;

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public void setPatchWidth(int patchWidth) {
        this.patchWidth = patchWidth;
    }

    public void setPatchHeight(int patchHeight) {
        this.patchHeight = patchHeight;
    }

    public void setFeatureOutputFactory(FeatureOutputFactory featureOutputFactory) {
        this.featureOutputFactory = featureOutputFactory;
    }

    protected abstract FeatureType[] getFeatureTypes();

    protected abstract Feature[] extractPatchFeatures(Product patchProduct);

    @Override
    public void initialize() throws OperatorException {

        if (featureOutputFactory == null) {
            initFeatureOutputFactory();
        }
        featureOutputFactory.setTargetPath(targetPath);

        if (skipFeaturesOutput) {
            System.out.println("Warning: Feature output skipped.");
        }
        if (skipProductOutput) {
            System.out.println("Warning: Product output skipped.");
        }
        if (skipRgbImageOutput) {
            System.out.println("Warning: RGB image output skipped.");
        }

        setTargetProduct(getSourceProduct());

        int productSizeX = getSourceProduct().getSceneRasterWidth();
        int productSizeY = getSourceProduct().getSceneRasterHeight();
        int patchCountX = (productSizeX + patchWidth - 1) / patchWidth;
        int patchCountY = (productSizeY + patchHeight - 1) / patchHeight;

        try {
            run(patchCountX, patchCountY);
        } catch (IOException e) {
            throw new OperatorException(e);
        }
    }

    private void run(int patchCountX, int patchCountY) throws IOException {
        Product sourceProduct = getSourceProduct();
        FeatureOutput featureOutput = featureOutputFactory.createFeatureOutput(sourceProduct);
        FeatureType[] featureTypes = getFeatureTypes();
        featureOutput.writeMetadata(featureTypes);

        for (int patchY = 0; patchY < patchCountY; patchY++) {
            for (int patchX = 0; patchX < patchCountX; patchX++) {

                Rectangle subsetRegion = createSubsetRegion(sourceProduct, patchY, patchX);
                Product patchProduct = createSubset(sourceProduct, subsetRegion);

                Feature[] features = extractPatchFeatures(patchProduct);
                if (features != null) {
                    featureOutput.writePatchFeatures(patchX, patchY, patchProduct, features);
                }

                patchProduct.dispose();
            }
        }

        featureOutput.close();
    }

    private void initFeatureOutputFactory() {
        try {
            Class<?> featureOutputFactoryClass = getClass().getClassLoader().loadClass(featureOutputFactoryClassName);
            this.featureOutputFactory = (FeatureOutputFactory) featureOutputFactoryClass.newInstance();
        } catch (ClassNotFoundException e) {
            throw new OperatorException(e);
        } catch (InstantiationException e) {
            throw new OperatorException(e);
        } catch (IllegalAccessException e) {
            throw new OperatorException(e);
        }
    }

    private Rectangle createSubsetRegion(Product sourceProduct, int tileY, int tileX) {
        final int productSizeX = sourceProduct.getSceneRasterWidth();
        final int productSizeY = sourceProduct.getSceneRasterHeight();
        final Rectangle sceneBoundary = new Rectangle(0, 0, productSizeX, productSizeY);
        final int x = tileX * patchWidth;
        final int y = tileY * patchHeight;
        return new Rectangle(x, y, patchWidth, patchHeight).intersection(sceneBoundary);
    }

    private Product createSubset(Product sourceProduct, Rectangle subsetRegion) {
        final HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("region", subsetRegion);
        return GPF.createProduct("Subset", parameters, sourceProduct);
    }

    static {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }

}
