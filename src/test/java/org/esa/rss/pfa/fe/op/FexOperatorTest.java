package org.esa.rss.pfa.fe.op;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.junit.Test;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * @author Norman Fomferra
 */
public class FexOperatorTest {

    static {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }

    @Test
    public void testOp() throws Exception {

        Product sourceProduct = new Product("P", "PT", 256, 260);
        sourceProduct.addBand("B1", "1");
        sourceProduct.addBand("B2", "2");

        MyFeatureOutputFactory outputFactory = new MyFeatureOutputFactory();
        assertEquals(null, outputFactory.featureOutput);

        FexOperator fexOperator = new MyFexOperator();
        fexOperator.setTargetPath("test");
        fexOperator.setOverwriteMode(true);
        fexOperator.setSkipProductOutput(true);
        fexOperator.setPatchWidth(100);
        fexOperator.setPatchHeight(100);
        fexOperator.setSourceProduct(sourceProduct);
        fexOperator.setFeatureOutputFactory(outputFactory);
        Product targetProduct = fexOperator.getTargetProduct();

        assertEquals("test", outputFactory.getTargetPath());
        assertEquals(true, outputFactory.isOverwriteMode());
        assertEquals(false, outputFactory.getSkipFeatureOutput());
        assertEquals(true, outputFactory.getSkipProductOutput());
        assertEquals(false, outputFactory.getSkipQuicklookOutput());

        assertSame(targetProduct, sourceProduct);
        assertNotNull(outputFactory.featureOutput);
        assertTrue(outputFactory.featureOutput.initialized);
        assertTrue(outputFactory.featureOutput.closed);
        assertEquals(9, outputFactory.featureOutput.patchOutputs.size());

        assertNotNull(outputFactory.featureOutput.patchOutputs.get(0).product);
        assertEquals(100, outputFactory.featureOutput.patchOutputs.get(0).product.getSceneRasterWidth());
        assertEquals(100, outputFactory.featureOutput.patchOutputs.get(0).product.getSceneRasterHeight());

        assertNotNull(outputFactory.featureOutput.patchOutputs.get(2).product);
        assertEquals(56, outputFactory.featureOutput.patchOutputs.get(2).product.getSceneRasterWidth());
        assertEquals(100, outputFactory.featureOutput.patchOutputs.get(2).product.getSceneRasterHeight());

        assertNotNull(outputFactory.featureOutput.patchOutputs.get(6).product);
        assertEquals(100, outputFactory.featureOutput.patchOutputs.get(6).product.getSceneRasterWidth());
        assertEquals(60, outputFactory.featureOutput.patchOutputs.get(6).product.getSceneRasterHeight());

        assertNotNull(outputFactory.featureOutput.patchOutputs.get(8).product);
        assertEquals(56, outputFactory.featureOutput.patchOutputs.get(8).product.getSceneRasterWidth());
        assertEquals(60, outputFactory.featureOutput.patchOutputs.get(8).product.getSceneRasterHeight());
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
        protected Feature[] extractPatchFeatures(int patchX, int patchY, Rectangle subsetRegion, Product patchProduct) {
            return new Feature[]{
                    new Feature(FEATURE_TYPES[0], "bibo"),
                    new Feature(FEATURE_TYPES[1], 3.14),
            };
        }
    }


    public static class MyFeatureOutputFactory extends FeatureOutputFactory {

        MyFeatureOutput featureOutput;
        String targetPath;

        @Override
        public FeatureOutput createFeatureOutput(Product sourceProduct) {
            featureOutput = new MyFeatureOutput();
            return featureOutput;
        }
    }

    private static class MyFeatureOutput implements FeatureOutput {
        ArrayList<MyPatchOutput> patchOutputs = new ArrayList<MyPatchOutput>();
        boolean initialized;
        boolean closed;

        @Override
        public void initialize(Product sourceProduct, FeatureType... featureTypes) throws IOException {
            initialized = true;
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
