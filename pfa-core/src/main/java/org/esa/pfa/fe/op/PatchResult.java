package org.esa.pfa.fe.op;

import java.io.Serializable;

/**
 * The result of a {@code FeatureWriter} operator for a single patch.
 *
 * @author Ralf Quast
 */
public class PatchResult implements Serializable {

    int patchX;

    int patchY;

    String featuresText;

    public PatchResult(int patchX, int patchY, String featuresText) {
        this.patchX = patchX;
        this.patchY = patchY;
        this.featuresText = featuresText;
    }

    public int getPatchX() {
        return patchX;
    }

    public int getPatchY() {
        return patchY;
    }

    public String getFeaturesText() {
        return featuresText;
    }
}
