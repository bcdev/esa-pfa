package org.esa.pfa.fe.op;

import com.bc.ceres.binding.PropertySet;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.pfa.fe.op.out.PatchSink;
import org.esa.pfa.fe.op.out.PatchWriter;
import org.esa.pfa.fe.op.out.PatchWriterFactory;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * @author Norman Fomferra
 */
public class FeatureWriterTest {

    static {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }

    @Test
    public void testOp() throws Exception {

        Product sourceProduct = new Product("P", "PT", 256, 260);
        sourceProduct.addBand("B1", "1");
        sourceProduct.addBand("B2", "2");

        MyPatchWriterFactory outputFactory = new MyPatchWriterFactory();
        assertEquals(null, outputFactory.featureOutput);

        FeatureWriter featureWriter = new MyFeatureWriter();
        featureWriter.setTargetDir(new File("test"));
        featureWriter.setOverwriteMode(true);
        featureWriter.setSkipProductOutput(true);
        featureWriter.setPatchWidth(100);
        featureWriter.setPatchHeight(100);
        featureWriter.setSourceProduct(sourceProduct);
        featureWriter.setPatchWriterFactory(outputFactory);
        Product targetProduct = featureWriter.getTargetProduct();

        assertEquals("test", outputFactory.getTargetPath());
        assertEquals(true, outputFactory.isOverwriteMode());
        assertEquals(false, outputFactory.getSkipFeatureOutput());
        assertEquals(true, outputFactory.getSkipProductOutput());
        assertEquals(false, outputFactory.getSkipQuicklookOutput());

        assertSame(targetProduct, sourceProduct);
        assertNotNull(outputFactory.featureOutput);
        assertTrue(outputFactory.featureOutput.initialized);
        assertEquals(0, outputFactory.featureOutput.patchOutputs.size());

        featureWriter.dispose();
        assertTrue(outputFactory.featureOutput.closed);
    }


    private static class MyFeatureWriter extends FeatureWriter {
        public static final FeatureType[] FEATURE_TYPES = new FeatureType[]{
                new FeatureType("f1", "d1", String.class),
                new FeatureType("f2", "d2", Double.class)
        };

        @Override
        protected FeatureType[] getFeatureTypes() {
            return FEATURE_TYPES;
        }

        @Override
        protected boolean processPatch(Patch patch, PatchSink sink) throws IOException {
            Feature[] bibos = {
                    new Feature(FEATURE_TYPES[0], "bibo"),
                    new Feature(FEATURE_TYPES[1], 3.14),
            };
            sink.writePatch(patch, bibos);
            return true;
        }
    }


    public static class MyPatchWriterFactory extends PatchWriterFactory {

        MyPatchWriter featureOutput;
        String targetPath;

        @Override
        public PatchWriter createPatchWriter(Product sourceProduct) {
            featureOutput = new MyPatchWriter();
            return featureOutput;
        }
    }

    private static class MyPatchWriter implements PatchWriter {
        ArrayList<MyPatchOutput> patchOutputs = new ArrayList<>();
        boolean initialized;
        boolean closed;

        @Override
        public void initialize(PropertySet configuration, Product sourceProduct, FeatureType... featureTypes) throws IOException {
            initialized = true;
        }

        @Override
        public void writePatch(Patch patch, Feature... features) throws IOException {
            MyPatchOutput patchOutput = new MyPatchOutput(patch, features);
            patchOutputs.add(patchOutput);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static class MyPatchOutput {
        Patch patch;
        Feature[] features;

        private MyPatchOutput(Patch patch, Feature[] features) {
            this.patch = patch;
            this.features = features;
        }
    }
}
