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
}
