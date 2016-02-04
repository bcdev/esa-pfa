package org.esa.pfa.fe.op.out;

import com.bc.ceres.binding.PropertySet;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.snap.core.datamodel.Product;

import java.io.Closeable;
import java.io.IOException;

/**
 * Writes all {@link org.esa.pfa.fe.op.Patch Patches} of a {@link Product} together with their {@link org.esa.pfa.fe.op.Feature Features} to a specific representation.
 *
 * @author Norman Fomferra
 */
public interface PatchWriter extends PatchSink, Closeable {
    void initialize(PropertySet configuration, Product sourceProduct, FeatureType... featureTypes) throws IOException;
}
