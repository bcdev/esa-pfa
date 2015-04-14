package org.esa.pfa.fe.op.out;

import com.bc.ceres.binding.PropertySet;
import org.esa.snap.framework.datamodel.Product;
import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;

import java.io.IOException;

/**
 * NOTE: This class is for testing only. Will be removed soon.
 *
 * @author Norman Fomferra
 */
public class HadoopPatchWriterFactory extends PatchWriterFactory {
    @Override
    public PatchWriter createPatchWriter(Product sourceProduct) throws IOException {
        return new MyPatchWriter(this, sourceProduct);
    }

    private static class MyPatchWriter implements PatchWriter {

        private final DefaultPatchWriter defaultPatchWriter;

        private MyPatchWriter(PatchWriterFactory patchWriterFactory, Product sourceProduct) throws IOException {
            defaultPatchWriter = new DefaultPatchWriter(patchWriterFactory, sourceProduct);
        }

        @Override
        public void initialize(PropertySet configuration, Product sourceProduct, FeatureType... featureTypes) throws IOException {
            defaultPatchWriter.initialize(configuration, sourceProduct, featureTypes);
            System.out.println("initialize(): sourceProduct = " + sourceProduct + ", foo = " + configuration.getValue("foo"));
        }

        @Override
        public void close() throws IOException {
            defaultPatchWriter.close();
            System.out.println("close()");

        }

        @Override
        public void writePatch(Patch patch, Feature... features) throws IOException {
            defaultPatchWriter.writePatch(patch, features);
            System.out.println("writePatch(): patch = " + patch);
            // todo - collect patch-->features
        }
    }
}
