package org.esa.rss.pfa.fe;

import org.esa.beam.framework.datamodel.Mask;

import java.awt.image.DataBufferByte;

/**
 * @author Norman Fomferra
 */
public class ConnectivityMetric {

    int connectionCount;
    double connectionRatio;
    int connectionCountMax;
    int occupiedCount;
    int borderCount;
    int insideCount;
    double fractalIndex;

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
        int connectionCount = 0;
        int occupiedCount = 0;
        int borderCount = 0;
        int insideCount = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean valC = isSet(data, width, x, y);
                if (valC) {
                    boolean valN = y <= 0 || isSet(data, width, x, y - 1);
                    boolean valS = y >= height - 1 || isSet(data, width, x, y + 1);
                    boolean valW = x <= 0 || isSet(data, width, x - 1, y);
                    boolean valE = x >= width - 1 || isSet(data, width, x + 1, y);
                    int neighborCount = 0;
                    if (valN)
                        neighborCount++;
                    if (valS)
                        neighborCount++;
                    if (valW)
                        neighborCount++;
                    if (valE)
                        neighborCount++;

                    connectionCount += neighborCount;
                    occupiedCount++;

                    if (neighborCount > 0 && neighborCount < 4)
                        borderCount++;
                    if (neighborCount == 4)
                        insideCount++;
                }
            }
        }

        this.occupiedCount = occupiedCount;
        this.connectionCount = connectionCount;
        this.connectionCountMax = 4 * width * height;
        connectionRatio = connectionCount / (double) connectionCountMax;
        this.borderCount = borderCount;
        this.insideCount = insideCount;
        fractalIndex = 2.0 - (insideCount > 0 ? insideCount / ((double) insideCount + (double) borderCount) : 0.0);
    }

    private boolean isSet(byte[] data, int width, int x, int y) {
        return data[y * width + x] != 0;
    }
}
