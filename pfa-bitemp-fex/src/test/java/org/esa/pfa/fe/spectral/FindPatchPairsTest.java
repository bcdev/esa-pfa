/*
 * Copyright (C) 2015 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.pfa.fe.spectral;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author marcoz
 */
public class FindPatchPairsTest {

    @Test
    public void testTimeDifference() throws Exception {
        int tdiff = FindPatchPairs.timeDifferenceInSeconds("X0118_Y0013_T200708030956.dim", "X0118_Y0013_T200708030956.dim");
        assertEquals(0, tdiff);

        tdiff = FindPatchPairs.timeDifferenceInSeconds("X0118_Y0013_T200708030956.dim", "X0118_Y0013_T200708030957.dim");
        assertEquals(60, tdiff);

        tdiff = FindPatchPairs.timeDifferenceInSeconds("X0118_Y0013_T200708030956.dim", "X0118_Y0013_T200708040956.dim");
        assertEquals(24*60*60, tdiff);
    }
}