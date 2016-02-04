package org.esa.pfa.fe.op.out;

import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.Patch;

import java.io.IOException;

/**
 * Writes the given {@link Patch} and it's {@link Feature}s to a specific representation.
 *
 * @author Norman Fomferra
 */
public interface PatchSink {
    void writePatch(Patch patch, Feature... features) throws IOException;
}
