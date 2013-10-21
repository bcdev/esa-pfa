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

public class ContagionIndexTest {

    @Test
    public void testCompute4x4_1() throws Exception {
        ContagionIndex cm = ContagionIndex.compute(4, 4, new byte[]{
                1, 1, 1, 1,
                1, 1, 1, 1,
                1, 1, 0, 0,
                1, 1, 0, 0,
        });

        assertEquals(3 + 3 + 3 + 3, cm.n00);

        assertEquals(5 + 2 + 2 + 0, cm.n01);

        assertEquals(1 + 2 + 2 + 2 + 2, cm.n10);

        assertEquals(3 + 5 + 5 + 3 + 5 + 7 + 6 + 3 + 5 + 6 + 3 + 3, cm.n11);

        assertEquals(1.0, cm.p00 + cm.p01 + cm.p10 + cm.p11, 0.0);

        assertTrue(cm.rc2 >= 0.0);

        assertTrue(cm.rc2 <= 1.0);

        System.out.println("cm.c = " + cm.rc2);
    }

    @Test
    public void testCompute4x4_2() throws Exception {
        ContagionIndex cm = ContagionIndex.compute(4, 4, new byte[]{
                1, 1, 1, 1,
                1, 1, 1, 1,
                0, 0, 0, 0,
                0, 0, 0, 0,
        });

        assertEquals(3 + 5 + 5 + 3 + 3 + 5 + 5 + 3, cm.n00);

        assertEquals(2 + 3 + 3 + 2, cm.n01);

        assertEquals(2 + 3 + 3 + 2, cm.n10);

        assertEquals(3 + 5 + 5 + 3 + 3 + 5 + 5 + 3, cm.n11);

        assertEquals(1.0, cm.p00 + cm.p01 + cm.p10 + cm.p11, 0.0);

        assertTrue(cm.rc2 >= 0.0);

        assertTrue(cm.rc2 <= 1.0);

        System.out.println("cm.c = " + cm.rc2);
    }

    @Test
    public void testCompute4x4_3() throws Exception {
        ContagionIndex cm = ContagionIndex.compute(4, 4, new byte[]{
                1, 0, 1, 0,
                0, 1, 0, 1,
                1, 0, 1, 0,
                0, 1, 0, 1,
        });

        assertEquals(2 + 1 + 2 + 4 + 4 + 2 + 1 + 2, cm.n00);

        assertEquals(3 + 2 + 3 + 4 + 4 + 3 + 2 + 3, cm.n01);

        assertEquals(2 + 3 + 4 + 3 + 3 + 4 + 3 + 2, cm.n10);

        assertEquals(1 + 2 + 4 + 2 + 2 + 4 + 2 + 1, cm.n11);

        assertEquals(1.0, cm.p00 + cm.p01 + cm.p10 + cm.p11, 0.0);

        assertTrue(cm.rc2 >= 0.0);

        assertTrue(cm.rc2 <= 1.0);

        System.out.println("cm.c = " + cm.rc2);
    }

    @Test
    public void testCompute5x5() throws Exception {
        ContagionIndex cm = ContagionIndex.compute(5, 5, new byte[]{
                1, 0, 1, 1, 1,
                1, 1, 0, 1, 1,
                1, 0, 1, 0, 1,
                0, 1, 0, 1, 0,
                0, 1, 0, 1, 0,
        });

        assertEquals(1 + 3 + 3 + 3 + 2 + 3 + 2 + 1 + 1 + 1, cm.n00);

        assertEquals(4 + 5 + 5 + 5 + 3 + 5 + 3 + 2 + 4 + 2, cm.n01);

        assertEquals(1 + 2 + 1 + 0 + 2 + 3 + 2 + 1 + 2 + 4 + 2 + 5 + 5 + 4 + 4, cm.n10);

        assertEquals(2 + 3 + 4 + 3 + 3 + 5 + 6 + 4 + 3 + 4 + 3 + 3 + 3 + 1 + 1, cm.n11);

        assertEquals(1.0, cm.p00 + cm.p01 + cm.p10 + cm.p11, 0.0);

        assertTrue(cm.rc2 >= 0.0);

        assertTrue(cm.rc2 <= 1.0);

        System.out.println("cm.c = " + cm.rc2);
    }

    @Test
    public void testCompute5x5_2() throws Exception {
        ContagionIndex cm = ContagionIndex.compute(5, 5, new byte[]{
                0, 1, 1, 1, 0,
                1, 1, 1, 1, 1,
                1, 1, 1, 1, 1,
                1, 1, 1, 1, 1,
                0, 1, 1, 1, 0,
        });

        assertEquals(0, cm.n00);

        assertEquals(12, cm.n01);

        assertEquals(12, cm.n10);

        assertEquals(120, cm.n11);

        assertEquals(1.0, cm.p00 + cm.p01 + cm.p10 + cm.p11, 0.0);

        assertTrue(cm.rc2 >= 0.0);

        assertTrue(cm.rc2 <= 1.0);

        System.out.println("cm.c = " + cm.rc2);
    }

    @Test
    public void testCompute5x5_3() throws Exception {
        ContagionIndex cm = ContagionIndex.compute(5, 5, new byte[]{
                1, 1, 0, 0, 0,
                1, 1, 1, 1, 0,
                0, 1, 1, 1, 0,
                0, 1, 1, 1, 1,
                0, 0, 0, 1, 1,
        });

        assertEquals(1 + 3 + 2 + 3 + 1 + 1 + 3 + 2 + 3 + 1, cm.n00);

        assertEquals(4 + 2 + 1 + 2 + 4 + 4 + 2 + 1 + 2 + 4, cm.n01);

        assertEquals(26, cm.n10, 1e-5);

        assertEquals(72, cm.n11, 1e-5);

        assertEquals(1.0, cm.p00 + cm.p01 + cm.p10 + cm.p11, 0.0);

        assertTrue(cm.rc2 >= 0.0);

        assertTrue(cm.rc2 <= 1.0);

        System.out.println("cm.c = " + cm.rc2);
    }

}
