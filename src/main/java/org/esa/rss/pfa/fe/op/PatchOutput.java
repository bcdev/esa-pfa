package org.esa.rss.pfa.fe.op;

import java.io.IOException;

/**
 * @author Norman Fomferra
 */
public interface PatchOutput {
    void writePatch(Patch patch, Feature... features) throws IOException;
}
