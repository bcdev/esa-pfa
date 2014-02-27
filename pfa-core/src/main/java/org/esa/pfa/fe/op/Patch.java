package org.esa.pfa.fe.op;

import org.esa.beam.framework.datamodel.Product;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Identifies a "patch" of a data product from which features are extracted.
 * The feature extraction subdivides data products into a regular raster of rectangular patches.
 *
 * @author Norman Fomferra
 * @author Luis Veci
 */
public final class Patch {
    public static final int LABEL_NONE = -1;
    public static final int LABEL_RELEVANT = 1;
    public static final int LABEL_IRRELEVANT = 0;

    private static int uidCnt = 0;
    private final int uid;

    private final int patchX;
    private final int patchY;
    private final Rectangle patchRegion;
    private final Product patchProduct;
    private final String patchName;
    private final List<Feature> featureList = new ArrayList<>(10);

    private int label;
    private double distance;   // functional distance of a patch to the hyperplane in SVM

    private String pathOnServer;
    private BufferedImage image;

    private final List<PatchListener> listenerList = new ArrayList<>(1);

    public Patch(int patchX, int patchY, Rectangle patchRegion, Product patchProduct) {
        this.uid = createUniqueID();
        this.patchX = patchX;
        this.patchY = patchY;
        this.patchName = String.format("x%02dy%02d", patchX, patchY);
        this.patchRegion = patchRegion;
        this.patchProduct = patchProduct;
        this.label = LABEL_NONE;
    }

    private synchronized int createUniqueID() {
        return uidCnt++;
    }

    public int getID() {
        return uid;
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

    public String getPathOnServer() {
        return pathOnServer;
    }

    public void setPathOnServer(final String path) {
        pathOnServer = path;
    }

    public void setImage(BufferedImage img) {
        image = img;
    }

    public BufferedImage getImage() {
        return image;
    }

    public void clearFeatures() {
        featureList.clear();
    }

    public void addFeature(final Feature fea) {
        featureList.add(fea);
    }

    public Feature[] getFeatures() {
        return featureList.toArray(new Feature[featureList.size()]);
    }

    public void setLabel(final int label) {
        this.label = label;
        updateState();
    }

    public int getLabel() {
        return label;
    }

    public void setDistance(final double distance) {
        this.distance = distance;
    }

    public double getDistance() {
        return distance;
    }


    private void updateState() {
        for(PatchListener listener : listenerList) {
            listener.notifyStateChanged(this);
        }
    }

    public void addListener(final PatchListener listener) {
        if (!listenerList.contains(listener)) {
            listenerList.add(listener);
        }
    }

    public void removeListener(final PatchListener listener) {
        listenerList.remove(listener);
    }

    public interface PatchListener {

        public void notifyStateChanged(final Patch patch);
    }

    public String getFeaturesAsText() {
        final StringBuilder str = new StringBuilder(100);

        for (Feature feature : featureList) {
            str.append(feature.getName());
            str.append(": ");
            str.append(feature.getValue().toString());
            str.append('\n');
        }
        if (str.length() == 0) {
            str.append("No features found");
        }
        return str.toString();
    }
}
