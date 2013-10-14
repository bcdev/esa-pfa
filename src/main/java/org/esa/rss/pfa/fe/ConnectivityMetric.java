package org.esa.rss.pfa.fe;

import org.esa.beam.framework.datamodel.Mask;

import java.awt.image.DataBufferByte;

/**
 * @author Norman Fomferra
 */
public class ConnectivityMetric {

    private int currentSectionLength;
    private int currentSectionLengthMax;

    private int lastValue;

    double meanMaxSectionLengthV;
    double meanMaxSectionLengthH;

    double sectionLengthRatio;

    private ConnectivityMetric() {
    }

    public static ConnectivityMetric compute(Mask mask) {
        ConnectivityMetric connectivityMetric = new ConnectivityMetric();
        connectivityMetric.run(mask);
        return connectivityMetric;
    }

    public static ConnectivityMetric compute(int width, int height, byte[] data) {
        ConnectivityMetric connectivityMetric = new ConnectivityMetric();
        connectivityMetric.run(width, height, data);
        return connectivityMetric;
    }

    private void run(Mask mask) {
        run(mask.getRasterWidth(), mask.getRasterHeight(),
            ((DataBufferByte) mask.getSourceImage().getData().getDataBuffer()).getData());
    }

    private void run(int width, int height, byte[] data) {
        meanMaxSectionLengthH = 0;

        for (int y = 0; y < height; y++) {
            initLineValueProcessing();
            for (int x = 0; x < width; x++) {
                processLineValue(data[(y * width + x)]);
            }
            processLineValue(0);
            //System.out.println("y = " + y + ", currentSectionLengthMax=" + currentSectionLengthMax);
            meanMaxSectionLengthH += currentSectionLengthMax;
        }
        meanMaxSectionLengthH /= height;

        meanMaxSectionLengthV = 0;

        for (int x = 0; x < width; x++) {
            initLineValueProcessing();
            for (int y = 0; y < height; y++) {
                processLineValue(data[(y * width + x)]);
            }
            processLineValue(0);
            //System.out.println("x = " + x + ", currentSectionLengthMax=" + currentSectionLengthMax);

            meanMaxSectionLengthV += currentSectionLengthMax;
        }
        meanMaxSectionLengthV /= width;

        sectionLengthRatio = (meanMaxSectionLengthH + meanMaxSectionLengthV) / (width  + height);
    }

    private void initLineValueProcessing() {
        currentSectionLengthMax = 0;
        currentSectionLength = 0;
        lastValue = 0;
    }

    private void processLineValue(int value) {
        if (value != 0) {
            currentSectionLength++;
        } else {
            if (value != lastValue) {
                if (currentSectionLength > currentSectionLengthMax) {
                    currentSectionLengthMax = currentSectionLength;
                }
                currentSectionLength = 0;
            }
        }
        lastValue = value;
    }
}
