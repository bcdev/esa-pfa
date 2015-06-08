/*
 * Copyright (C) 2013 by Array Systems Computing Inc. http://www.array.ca
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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AlgalBloomApplicationDescriptorTest {

    @Test
    public void testPattern() throws Exception {
        AlgalBloomApplicationDescriptor descriptor = new AlgalBloomApplicationDescriptor();

        PFAApplicationDescriptor.ProductNameResolver productNameResolver = descriptor.getProductNameResolver();
        assertNotNull(productNameResolver);
        assertEquals("/testdata/2008/07/13/MER_RR__1PRACR20080713_184950_000026382070_00185_33305_0000.N1",
                     productNameResolver.resolve("/testdata/${yyyy}/${MM}/${dd}/${name}",
                                                 "MER_RR__1PRACR20080713_184950_000026382070_00185_33305_0000.N1"));

        String defaultDataAccessPattern = descriptor.getDefaultDataAccessPattern();
        assertNotNull(defaultDataAccessPattern);

        assertEquals(true, defaultDataAccessPattern.contains("${name}"));
        String resolved = productNameResolver.resolve(defaultDataAccessPattern,
                                                     "MER_RR__1PRACR20080713_184950_000026382070_00185_33305_0000.N1");
        assertEquals(false, resolved.contains("${yyyy}"));
        assertEquals(false, resolved.contains("${MM}"));
        assertEquals(false, resolved.contains("${dd}"));
        assertEquals(false, resolved.contains("${name}"));
    }
}
