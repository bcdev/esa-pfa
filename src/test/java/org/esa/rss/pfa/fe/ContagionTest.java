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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContagionTest {

    @Test
    public void testCompute4x4() throws Exception {
        Contagion cm = Contagion.computeQueen(4, 4, new byte[]{
                1, 1, 1, 1,
                1, 1, 1, 1,
                1, 1, 0, 0,
                1, 1, 0, 0,
        });

        assertEquals(4, cm.n0);

        assertEquals(12, cm.n1);

        assertEquals(4, cm.n00);

        assertEquals(3, cm.n01);

        assertEquals(5, cm.n10);

        assertEquals(12, cm.n11);

        assertEquals(4.0 / 4.0, cm.p00, 0.0);

        assertEquals(3.0 / 4.0, cm.p01, 0.0);

        assertEquals(5.0 / 12.0, cm.p10, 0.0);

        assertEquals(12.0 / 12.0, cm.p11, 0.0);

        assertTrue(cm.c >= 0.0);

        assertTrue(cm.c <= 1.0);

        System.out.println("cm.c = " + cm.c);
    }

    @Test
    public void testCompute5x5() throws Exception {
        Contagion cm = Contagion.computeQueen(5, 5, new byte[]{
                1, 0, 1, 1, 1,
                1, 1, 0, 1, 1,
                1, 0, 1, 0, 1,
                0, 1, 0, 1, 0,
                0, 1, 0, 1, 0,
        });

        assertEquals(10, cm.n0);

        assertEquals(15, cm.n1);

        assertEquals(10, cm.n00);

        assertEquals(10, cm.n01);

        assertEquals(14, cm.n10);

        assertEquals(15, cm.n11);

        assertEquals(10.0 / 10.0, cm.p00, 0.0);

        assertEquals(10.0 / 10.0, cm.p01, 0.0);

        assertEquals(14.0 / 15.0, cm.p10, 0.0);

        assertEquals(15.0 / 15.0, cm.p11, 0.0);

        assertTrue(cm.c >= 0.0);

        assertTrue(cm.c <= 1.0);

        System.out.println("cm.c = " + cm.c);
    }

    @Test
    public void testCompute5x5_2() throws Exception {
        Contagion cm = Contagion.computeQueen(5, 5, new byte[]{
                0, 1, 1, 1, 0,
                1, 1, 1, 1, 1,
                1, 1, 1, 1, 1,
                1, 1, 1, 1, 1,
                0, 1, 1, 1, 0,
        });

        assertEquals(4, cm.n0);

        assertEquals(21, cm.n1);

        assertEquals(0, cm.n00);

        assertEquals(4, cm.n01);

        assertEquals(12, cm.n10);

        assertEquals(21, cm.n11);

        assertEquals(0.0 / 4.0, cm.p00, 0.0);

        assertEquals(4.0 / 4.0, cm.p01, 0.0);

        assertEquals(12.0 / 21.0, cm.p10, 0.0);

        assertEquals(21.0 / 21.0, cm.p11, 0.0);

        assertTrue(cm.c >= 0.0);

        assertTrue(cm.c <= 1.0);

        System.out.println("cm.c = " + cm.c);
    }

    @Test
    public void testCompute5x5_3() throws Exception {
        Contagion cm = Contagion.computeQueen(5, 5, new byte[]{
                1, 1, 0, 0, 0,
                1, 1, 1, 1, 0,
                0, 1, 1, 1, 0,
                0, 1, 1, 1, 1,
                0, 0, 0, 1, 1,
        });

        assertEquals(10, cm.n0, 1e-5);

        assertEquals(15, cm.n1, 1e-5);

        assertEquals(10, cm.n00, 1e-5);

        assertEquals(10, cm.n01, 1e-5);

        assertEquals(12, cm.n10, 1e-5);

        assertEquals(15, cm.n11, 1e-5);

        assertEquals(10.0 / 10.0, cm.p00, 0.0);

        assertEquals(10.0 / 10.0, cm.p01, 0.0);

        assertEquals(12.0 / 15.0, cm.p10, 0.0);

        assertEquals(15.0 / 15.0, cm.p11, 0.0);

        assertTrue(cm.c >= 0.0);

        assertTrue(cm.c <= 1.0);

        System.out.println("cm.c = " + cm.c);
    }

    private static class Contagion {

        private static final double normalizingDenominator = 2.0 * Math.log(2.0);

        private final boolean queenNeighborhood;

        public int n0;
        public int n1;

        public int m00;
        public int m01;
        public int m10;
        public int m11;
        public int n00;
        public int n01;
        public int n10;
        public int n11;

        public double p00;
        public double p01;
        public double p10;
        public double p11;

        public double c;

        public Contagion(boolean queenNeighborhood) {
            this.queenNeighborhood = queenNeighborhood;
        }

        public static Contagion computeQueen(int width, int height, byte[] data) {
            final Contagion contagion = new Contagion(true);
            contagion.run(width, height, data);
            return contagion;
        }

        public static Contagion computeRook(int width, int height, byte[] data) {
            final Contagion contagion = new Contagion(false);
            contagion.run(width, height, data);
            return contagion;
        }

        private void run(int width, int height, byte[] data) {
            n0 = 0;
            n1 = 0;

            n00 = 0;
            n01 = 0;
            n10 = 0;
            n11 = 0;

            // the manner in which nij is counted here makes sense, since it guarantees that nij < ni, whereas
            // mij > ni in general. So one cannot use mij to compute probabilities
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    final int centerIndex = y * width + x;
                    final byte centerValue = data[centerIndex];
                    if (centerValue == 0) {
                        n0++;
                    } else {
                        n1++;
                    }

                    m00 = 0;
                    m01 = 0;
                    m10 = 0;
                    m11 = 0;

                    final boolean canGoEast = x + 1 < width;
                    final boolean canGoWest = x > 0;
                    final boolean canGoNorth = y > 0;
                    final boolean canGoSouth = y + 1 < height;
                    if (canGoEast) {
                        count(centerValue, data[centerIndex + 1]);
                    }
                    if (canGoWest) {
                        count(centerValue, data[centerIndex - 1]);
                    }
                    if (canGoNorth) {
                        final int northernIndex = centerIndex - width;
                        final byte northernValue = data[northernIndex];
                        count(centerValue, northernValue);
                        if (queenNeighborhood) {
                            if (canGoEast) {
                                if (centerValue != data[northernIndex + 1]) {
                                    count(centerValue, data[northernIndex + 1]);
                                }
                            }
                            if (canGoWest) {
                                if (centerValue != data[northernIndex - 1]) {
                                    count(centerValue, data[northernIndex - 1]);
                                }
                            }
                        }
                    }
                    if (canGoSouth) {
                        final int southernIndex = centerIndex + width;
                        final byte southernValue = data[southernIndex];
                        count(centerValue, southernValue);
                        if (queenNeighborhood) {
                            if (canGoEast) {
                                count(centerValue, data[southernIndex + 1]);
                            }
                            if (canGoWest) {
                                count(centerValue, data[southernIndex - 1]);
                            }
                        }
                    }

                    if (m00 > 0) {
                        n00++;
                    }
                    if (m01 > 0) {
                        n01++;
                    }
                    if (m10 > 0) {
                        n10++;
                    }
                    if (m11 > 0) {
                        n11++;
                    }
                }
            }

            // pij here corresponds to q_i,j on the slides, note that pij is always in [0, 1]
            p00 = (double) n00 / (double) n0;
            p01 = (double) n01 / (double) n0;
            p10 = (double) n10 / (double) n1;
            p11 = (double) n11 / (double) n1;

            // using the queen neighborhood produces neither very low nor very high values for the test cases
            // todo - try rook neighborhood only
            c = (1.0 + term(p00) + term(p01) + term(p10) + term(p11)) / normalizingDenominator;
        }

        private void count(byte centerValue, byte neighborValue) {
            if (centerValue == 0) {
                if (neighborValue == 0) {
                    m00++;
                } else {
                    m01++;
                }
            } else {
                if (neighborValue == 0) {
                    m10++;
                } else {
                    m11++;
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
}
