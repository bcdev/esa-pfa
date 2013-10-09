package org.esa.rss.pfa.fe.op;

import org.esa.beam.framework.datamodel.Product;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

/**
 * @author Norman Fomferra
 */
public class FexOperatorTest {

    @Test
    public void testOp() throws Exception {

        Product sourceProduct = new Product("P", "PT", 256, 260);
        sourceProduct.addBand("B1", "1");
        sourceProduct.addBand("B2", "2");

        MyFeatureOutputFactory outputFactory = new MyFeatureOutputFactory();
        Assert.assertNull(outputFactory.featureOutput);

        FexOperator fexOperator = new MyFexOperator();
        fexOperator.setTargetPath("test");
        fexOperator.setPatchWidth(100);
        fexOperator.setPatchHeight(100);
        fexOperator.setSourceProduct(sourceProduct);
        fexOperator.setFeatureOutputFactory(outputFactory);
        Product targetProduct = fexOperator.getTargetProduct();

        Assert.assertEquals("test", outputFactory.targetPath);
        Assert.assertSame(targetProduct, sourceProduct);
        Assert.assertNotNull(outputFactory.featureOutput);
        Assert.assertTrue(outputFactory.featureOutput.metadataWritten);
        Assert.assertTrue(outputFactory.featureOutput.closed);
        Assert.assertEquals(9, outputFactory.featureOutput.patchOutputs.size());

        Assert.assertNotNull(outputFactory.featureOutput.patchOutputs.get(0).product);
        Assert.assertEquals(100, outputFactory.featureOutput.patchOutputs.get(0).product.getSceneRasterWidth());
        Assert.assertEquals(100, outputFactory.featureOutput.patchOutputs.get(0).product.getSceneRasterHeight());

        Assert.assertNotNull(outputFactory.featureOutput.patchOutputs.get(2).product);
        Assert.assertEquals(56, outputFactory.featureOutput.patchOutputs.get(2).product.getSceneRasterWidth());
        Assert.assertEquals(100, outputFactory.featureOutput.patchOutputs.get(2).product.getSceneRasterHeight());

        Assert.assertNotNull(outputFactory.featureOutput.patchOutputs.get(6).product);
        Assert.assertEquals(100, outputFactory.featureOutput.patchOutputs.get(6).product.getSceneRasterWidth());
        Assert.assertEquals(60, outputFactory.featureOutput.patchOutputs.get(6).product.getSceneRasterHeight());

        Assert.assertNotNull(outputFactory.featureOutput.patchOutputs.get(8).product);
        Assert.assertEquals(56, outputFactory.featureOutput.patchOutputs.get(8).product.getSceneRasterWidth());
        Assert.assertEquals(60, outputFactory.featureOutput.patchOutputs.get(8).product.getSceneRasterHeight());
    }


    private static class MyFexOperator extends FexOperator {
        public static final FeatureType[] FEATURE_TYPES = new FeatureType[]{
                new FeatureType("f1", "d1", String.class),
                new FeatureType("f2", "d2", Double.class)
        };

        @Override
        protected FeatureType[] getFeatureTypes() {
            return FEATURE_TYPES;
        }

        @Override
        protected Feature[] extractPatchFeatures(Product patchProduct) {
            return new Feature[]{
                    new Feature<String>(FEATURE_TYPES[0], "bibo"),
                    new Feature<Double>(FEATURE_TYPES[1], 3.14),
            };
        }
    }


    public static class MyFeatureOutputFactory implements FeatureOutputFactory {

        MyFeatureOutput featureOutput;
        String targetPath;

        @Override
        public void setTargetPath(String targetPath) {
            this.targetPath = targetPath;
        }

        @Override
        public FeatureOutput createFeatureOutput(Product sourceProduct) {
            featureOutput = new MyFeatureOutput();
            return featureOutput;
        }
    }

    private static class MyFeatureOutput implements FeatureOutput {
        ArrayList<MyPatchOutput> patchOutputs = new ArrayList<MyPatchOutput>();
        boolean metadataWritten;
        boolean closed;

        @Override
        public void writeMetadata(FeatureType... featureTypes) {
            metadataWritten = true;
        }

        @Override
        public void writePatchFeatures(int patchX, int patchY, Product product, Feature... features) throws IOException {
            MyPatchOutput patchOutput = new MyPatchOutput(product, features);
            patchOutputs.add(patchOutput);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static class MyPatchOutput {
        Product product;
        Feature[] features;

        private MyPatchOutput(Product product, Feature[] features) {
            this.product = product;
            this.features = features;
        }
    }
}
