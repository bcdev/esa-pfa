package org.esa.pfa.fe.op;

import org.esa.snap.framework.datamodel.Product;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.List;

/**
 * Identifies a "patch" of a data product from which features are extracted.
 * The feature extraction subdivides data products into a regular raster of rectangular patches.
 *
 * @author Norman Fomferra
 * @author Luis Veci
 */
public final class Patch {

    public enum Label {
        NONE(-1), RELEVANT(1), IRRELEVANT(0);

        private final int value;

        Label(int value) {

            this.value = value;
        }

        public int getValue() {
            return value;
        }

    }

    private static int uidCnt = 0;

    private final int uid;

    private final String parentProductName;
    private final int patchX;
    private final int patchY;
    private final Rectangle patchRegion;
    private final Product patchProduct;

    private List<Feature> featureList = new ArrayList<>(10);
    private Map<String, BufferedImage> imageMap = new HashMap<>();
    private List<PatchListener> listenerList = new ArrayList<>(1);

    private Label label;
    private double distance;   // functional distance of a patch to the hyperplane in SVM
    private double[] featureValues;

    public Patch(String parentProductName, int patchX, int patchY) {
        this(parentProductName, patchX, patchY, null, null);
    }

    public Patch(String parentProductName, int patchX, int patchY, Rectangle patchRegion, Product patchProduct) {
        this.parentProductName = parentProductName;
        this.patchX = patchX;
        this.patchY = patchY;
        this.patchRegion = patchRegion;
        this.patchProduct = patchProduct;
        this.uid = createUniqueID();
        this.label = Label.NONE;
    }

    private synchronized int createUniqueID() {
        return uidCnt++;
    }

    public int getID() {
        return uid;
    }

    public String getPatchName() {
        return String.format("x%03dy%03d", patchX, patchY);
    }

    public int getPatchX() {
        return patchX;
    }

    public int getPatchY() {
        return patchY;
    }

    public String getParentProductName() {
        return parentProductName;
    }

    public Rectangle getPatchRegion() {
        return patchRegion;
    }

    public Product getPatchProduct() {
        return patchProduct;
    }

    public void setImage(final String name, final BufferedImage img) {
        imageMap.put(name, img);
    }

    public BufferedImage getImage(final String name) {
        return imageMap.get(name);
    }

    public synchronized double[] getFeatureValues() {
        if (featureValues == null) {
            featureValues = getFeatureValues(featureList);
        }
        return featureValues;
    }

    public void clearFeatures() {
        featureList.clear();
    }

    public void addFeature(final Feature fea) {
        featureList.add(fea);
    }

    public void setFeatures(final Feature... features) {
        featureList.clear();
        Collections.addAll(featureList, features);
    }

    public Feature[] getFeatures() {
        return featureList.toArray(new Feature[featureList.size()]);
    }

    public void setLabel(final Label label) {
        this.label = label;
        updateState();
    }

    public Label getLabel() {
        return label;
    }

    public void setDistance(final double distance) {
        this.distance = distance;
    }

    public double getDistance() {
        return distance;
    }

    private Patch readResolve() {
        featureList = new ArrayList<>(10);
        imageMap = new HashMap<>();
        listenerList = new ArrayList<>(1);
        return this;
    }

    private void updateState() {
        for (PatchListener listener : listenerList) {
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

    public void readFeatureFile(File featureFile, FeatureType[] effectiveFeatureTypes) throws IOException {
        if (featureFile.exists()) {
            try (FileReader reader = new FileReader(featureFile)) {
                readFeatures(reader, effectiveFeatureTypes);
            }
        }
    }

    public void readFeatures(Reader reader, FeatureType[] effectiveFeatureTypes) throws IOException {
        final Properties featureValues = new Properties();
        featureValues.load(reader);
        clearFeatures();
        for (FeatureType featureType : effectiveFeatureTypes) {
            final String featureValue = featureValues.getProperty(featureType.getName());
            if (featureValue != null) {
                addFeature(createFeature(featureType, featureValue));
            }
        }
    }

    private static Feature createFeature(FeatureType feaType, String value) {
        final Class<?> valueType = feaType.getValueType();
        if (value.equals("NaN")) {
            value = "0";
        }

        if (Double.class.isAssignableFrom(valueType)) {
            return new Feature(feaType, Double.parseDouble(value));
        } else if (Float.class.isAssignableFrom(valueType)) {
            return new Feature(feaType, Float.parseFloat(value));
        } else if (Integer.class.isAssignableFrom(valueType)) {
            return new Feature(feaType, Integer.parseInt(value));
        } else if (Boolean.class.isAssignableFrom(valueType)) {
            return new Feature(feaType, Boolean.parseBoolean(value));
        } else if (Character.class.isAssignableFrom(valueType)) {
            return new Feature(feaType, value);
        } else if (String.class.isAssignableFrom(valueType)) {
            return new Feature(feaType, value);
        }
        return null;
    }

    public static double[] getFeatureValues(final List<Feature> features) {
        double[] values = new double[features.size()];
        for (int i = 0; i < features.size(); i++) {
            Object value = features.get(i).getValue();
            if (value instanceof Number) {
                values[i] = ((Number) value).doubleValue();
            } else {
                throw new IllegalArgumentException("feature is not numeric.");
            }
        }
        return values;
    }
}
