/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.pfa.fe;

import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.Tile;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class RegionGrower {

    private final Tile srcTile;
    private int maxClusterSize = 0;
    private int numSamples = 0;

    public RegionGrower(final Tile srcTile) {
        this.srcTile = srcTile;
    }

    public int getMaxClusterSize() {
        return maxClusterSize;
    }

    public int getNumSamples() {
        return numSamples;
    }

    public void run(final double threshold, final double[] dataArray) {
        try {
            final int tx0 = srcTile.getMinX();
            final int ty0 = srcTile.getMinY();
            final int tw = srcTile.getWidth();
            final int th = srcTile.getHeight();

            final ProductData srcData = srcTile.getDataBuffer();
            final int[][] pixelsScaned = new int[th][tw];
            final List<PixelPos> clusterPixels = new ArrayList<>(2000);

            int cnt = 0;
            final int maxy = ty0 + th;
            final int maxx = tx0 + tw;
            for (int ty = ty0; ty < maxy; ty++) {

                for (int tx = tx0; tx < maxx; tx++) {

                    int index = srcTile.getDataBufferIndex(tx, ty);
                    double val = srcData.getElemDoubleAt(index);
                    dataArray[cnt++] = val;
                    if (val > threshold) {
                        numSamples++;

                        if(pixelsScaned[ty-ty0][tx-tx0] == 0) {
                            clusterPixels.clear();
                            clustering(tx, ty, tx0, ty0, tw, th, srcData, srcTile, threshold, pixelsScaned, clusterPixels);

                            if(clusterPixels.size() > maxClusterSize) {
                                maxClusterSize = clusterPixels.size();
                            }

                           /* if (clusterPixels.size() >= minClusterSizeInPixels) {
                                for (PixelPos pixel : clusterPixels) {

                                }
                            }  */
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Find pixels detected as target in a 3x3 window centered at a given point.
     * @param xc The x coordinate of the given point.
     * @param yc The y coordinate of the given point.
     * @param x0 The x coordinate for the upper left corner point of the source rectangle.
     * @param y0 The y coordinate for the upper left corner point of the source rectangle.
     * @param w The width of the source rectangle.
     * @param h The height of the source rectangle.
     * @param data The bit maks band data.
     * @param tile The bit mask band tile.
     * @param pixelsScaned The binary array indicating which pixel in the tile has been scaned.
     * @param clusterPixels The list of pixels in the cluster.
     */
    private static void clustering(final int xc, final int yc, final int x0, final int y0, final int w, final int h,
                                   final ProductData data, final Tile tile, final double threshold,
                                   final int[][] pixelsScaned, final List<PixelPos> clusterPixels) {

        final List<PixelPos> seeds = new ArrayList<>();
        seeds.add(new PixelPos(xc, yc));
        pixelsScaned[yc - y0][xc - x0] = 1;
        clusterPixels.add(new PixelPos(xc, yc));

        while (seeds.size() > 0) {
            final List<PixelPos> newSeeds = new ArrayList<>();
            for (PixelPos pixel : seeds) {
                searchNeighbourhood(pixel, x0, y0, w, h, data, tile, threshold, pixelsScaned, clusterPixels, newSeeds);
            }
            seeds.clear();
            seeds.addAll(newSeeds);
        }
    }

    private static void searchNeighbourhood(final PixelPos pixel, final int x0, final int y0, final int w, final int h,
                                            final ProductData data, final Tile tile, final double threshold,
                                            final int[][] pixelsScaned, final List<PixelPos> clusterPixels,
                                            final List<PixelPos> newSeeds) {

        final int xc = (int)pixel.x;
        final int yc = (int)pixel.y;
        final int[] x = {xc-1,   xc, xc+1, xc-1, xc+1, xc-1,   xc, xc+1};
        final int[] y = {yc-1, yc-1, yc-1,   yc,   yc, yc+1, yc+1, yc+1};

        for (int i = 0; i < 8; i++) {
            if (x[i] >= x0 && x[i] < x0 + w && y[i] >= y0 && y[i] < y0 + h &&
                    pixelsScaned[y[i] - y0][x[i] - x0] == 0 &&
                    data.getElemDoubleAt(tile.getDataBufferIndex(x[i], y[i])) > threshold) {

                pixelsScaned[y[i] - y0][x[i] - x0] = 1;
                clusterPixels.add(new PixelPos(x[i], y[i]));
                newSeeds.add(new PixelPos(x[i], y[i]));
            }
        }
    }
}
