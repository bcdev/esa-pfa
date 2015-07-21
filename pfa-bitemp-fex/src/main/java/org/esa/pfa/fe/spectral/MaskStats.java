package org.esa.pfa.fe.spectral;

import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.snap.framework.datamodel.Mask;

import java.awt.image.DataBufferByte;

/**
 * @author Norman Fomferra
 */
public class MaskStats {

    public static int countPixels(Mask mask) {
        return countPixels(mask.getSourceImage());
    }

    public static int countPixels(MultiLevelImage sourceImage) {
        // Brute force processing: get ALL data
        final byte[] data = ((DataBufferByte) sourceImage.getData().getDataBuffer()).getData();
        final int width = sourceImage.getWidth();
        final int height = sourceImage.getHeight();

        int count = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (data[y * width + x] != 0) {
                    count++;
                }
            }
        }

        return count;
    }

    public static double maskedRatio(MultiLevelImage sourceImage) {
        double count = countPixels(sourceImage);
        final int width = sourceImage.getWidth();
        final int height = sourceImage.getHeight();
        return count / width / height;
    }

    public static int countPixels(Mask roi1, Mask roi2) {
        return countPixels(roi1.getSourceImage(), roi2.getSourceImage());
    }

    public static int countPixels(MultiLevelImage sourceImage1, MultiLevelImage sourceImage2) {
        // Brute force processing: get ALL data
        final byte[] data1 = ((DataBufferByte) sourceImage1.getData().getDataBuffer()).getData();
        final byte[] data2 = ((DataBufferByte) sourceImage2.getData().getDataBuffer()).getData();
        final int width = sourceImage1.getWidth();
        final int height = sourceImage1.getHeight();

        int count = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (data1[y * width + x] != 0 && data2[y * width + x] != 0) {
                    count++;
                }
            }
        }

        return count;
    }

}
