package org.esa.pfa.db;

import org.esa.pfa.fe.op.Patch;

import java.io.IOException;

/**
 * Query the index for {@link Patch Patches}.
 */
public interface QueryInterface {

    int getNumPatchesInDatabase();

    Patch getPatch(final int patchIndex) throws IOException;

    Patch[] getRandomPatches(final int numPatches) throws IOException;

    Patch[] query(String queryExpr, int hitCount) throws IOException;
}
