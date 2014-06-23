package org.esa.pfa.fe.op;

/**
 * @author Ralf Quast
 */
public class PatchResult {

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
