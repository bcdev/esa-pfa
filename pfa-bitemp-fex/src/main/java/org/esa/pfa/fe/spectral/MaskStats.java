package org.esa.pfa.fe.spectral;

import org.esa.snap.framework.datamodel.Mask;

import java.awt.image.DataBufferByte;

/**
 * @author Norman Fomferra
 */
public class MaskStats {

    public static int countPixels(Mask mask) {

        // Brute force processing: get ALL data
        final byte[] data = ((DataBufferByte) mask.getSourceImage().getData().getDataBuffer()).getData();
        final int width = mask.getRasterWidth();

        int count = 0;

        for (int y = 0; y < mask.getRasterHeight(); y++) {
            for (int x = 0; x < width; x++) {
                if (data[y * width + x] != 0) {
                    count++;
                }
            }
        }

        return count;
    }
}
