package org.esa.rss.pfa.fe;/*
 * Copyright (C) 2012 Brockmann Consult GmbH (info@brockmann-consult.de)
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

import org.esa.beam.framework.datamodel.Mask;

/**
 * For computing the contagion index, see Li & Reynolds (1993, Landscape Ecology vol. 8 no. 3 pp 155-162)
 * http://andrewsforest.oregonstate.edu/pubs/pdf/pub1502.pdf
 *
 * @author Ralf Quast
 */
class ContagionIndex {

    private static final int N = 2;
    private static final double EE_MAX = 2.0 * Math.log(N);

    private final boolean queenNeighborhood;

    public int n00;
    public int n01;
    public int n10;
    public int n11;

    public double p00;
    public double p01;
    public double p10;
    public double p11;

    public double rc2;

    public ContagionIndex(boolean queenNeighborhood) {
        this.queenNeighborhood = queenNeighborhood;
    }

    public static ContagionIndex compute(Mask mask) {
        ContagionIndex contagionIndex = new ContagionIndex(true);
        contagionIndex.run(mask);
        return contagionIndex;
    }

    public static ContagionIndex compute(int width, int height, byte[] data) {
        return compute(width, height, data, true);
    }

    private static ContagionIndex compute(int width, int height, byte[] data, boolean queenNeighborhood) {
        final ContagionIndex contagionIndex = new ContagionIndex(queenNeighborhood);
        contagionIndex.run(width, height, data);
        return contagionIndex;
    }

    public void run(Mask mask) {
        final int w = mask.getRasterWidth();
        final int h = mask.getRasterHeight();
        final byte[] data = new byte[w * h];
        mask.getSourceImage().getData().getDataElements(0, 0, w, h, data);
        run(w, h, data);
    }

    private void run(int width, int height, byte[] data) {
        n00 = 0;
        n01 = 0;
        n10 = 0;
        n11 = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int centerIndex = y * width + x;
                final byte centerValue = data[centerIndex];

                final boolean goEast = x + 1 < width;
                final boolean goWest = x > 0;
                final boolean goNorth = y > 0;
                final boolean goSouth = y + 1 < height;

                if (goEast) {
                    count(centerValue, data[centerIndex + 1]);
                }
                if (goWest) {
                    count(centerValue, data[centerIndex - 1]);
                }
                if (goNorth) {
                    final int northernIndex = centerIndex - width;
                    count(centerValue, data[northernIndex]);
                    if (queenNeighborhood) {
                        if (goEast) {
                            count(centerValue, data[northernIndex + 1]);
                        }
                        if (goWest) {
                            count(centerValue, data[northernIndex - 1]);
                        }
                    }
                }
                if (goSouth) {
                    final int southernIndex = centerIndex + width;
                    count(centerValue, data[southernIndex]);
                    if (queenNeighborhood) {
                        if (goEast) {
                            count(centerValue, data[southernIndex + 1]);
                        }
                        if (goWest) {
                            count(centerValue, data[southernIndex - 1]);
                        }
                    }
                }
            }
        }

        // Eq. (6)
        final int adjacentPairCount = n00 + n01 + n10 + n11;
        p00 = (double) (n00) / (double) adjacentPairCount; // P_0 * P_{0|0}
        p01 = (double) (n01) / (double) adjacentPairCount; // P_0 * P_{1|0}
        p10 = (double) (n10) / (double) adjacentPairCount; // P_1 * P_{0|1}
        p11 = (double) (n11) / (double) adjacentPairCount; // P_1 * P_{1|1}

        // Eq. (23)
        rc2 = 1.0 + (term(p00) + term(p01) + term(p10) + term(p11)) / EE_MAX;
    }

    private void count(byte centerValue, byte neighborValue) {
        if (centerValue == 0) {
            if (neighborValue == 0) {
                n00++;
            } else {
                n01++;
            }
        } else {
            if (neighborValue == 0) {
                n10++;
            } else {
                n11++;
            }
        }
    }

    private static double term(double x) {
        if (x != 0.0) {
            return x * Math.log(x);
        } else {
            return 0.0; // lim_{x->0} x * log(x) = 0
        }
    }
}
