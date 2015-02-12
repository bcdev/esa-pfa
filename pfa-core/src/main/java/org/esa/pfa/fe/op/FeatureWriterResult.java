package org.esa.pfa.fe.op;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result of a {@code FeatureWriter} operator.
 *
 * @author Ralf Quast
 */
public final class FeatureWriterResult implements Serializable {

    private String productName;

    private List<PatchResult> patchResults;

    public FeatureWriterResult(String productName) {
        this.productName = productName;
        this.patchResults = Collections.synchronizedList(new ArrayList<PatchResult>());
    }

    public String getProductName() {
        return productName;
    }

    public List<PatchResult> getPatchResults() {
        return patchResults;
    }

    public void addPatchResult(int patchX, int patchY, String featuresText) {
        patchResults.add(new PatchResult(patchX, patchY, featuresText));
    }
}
