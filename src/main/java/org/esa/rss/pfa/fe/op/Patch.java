package org.esa.rss.pfa.fe.op;

import org.esa.beam.framework.datamodel.Product;

import java.awt.*;

/**
 * @author Norman Fomferra
 */
public final class Patch {


    private final int patchX;
    private final int patchY;
    private final Rectangle patchRegion;
    private final Product patchProduct;
    private final String patchName;

    public Patch(int patchX, int patchY, Rectangle patchRegion, Product patchProduct) {
        this.patchX = patchX;
        this.patchY = patchY;
        this.patchName = String.format("x%02dy%02d", patchX, patchY);
        this.patchRegion = patchRegion;
        this.patchProduct = patchProduct;
    }

    public String getPatchName() {
        return patchName;
    }

    public int getPatchX() {
        return patchX;
    }

    public int getPatchY() {
        return patchY;
    }

    public Rectangle getPatchRegion() {
        return patchRegion;
    }

    public Product getPatchProduct() {
        return patchProduct;
    }
}
