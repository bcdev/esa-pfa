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

package org.esa.pfa.db;

import org.esa.pfa.fe.op.DatasetDescriptor;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * @author marcoz
 */
public class QueryInterfaceBenchmark {
    public static void main(String[] args) throws IOException {
        Path dbPath = Paths.get(args[0]);
        System.out.println("dbPath = " + dbPath);

        long t1 = System.currentTimeMillis();
        DatasetDescriptor dsDescriptor = DatasetDescriptor.read(new File(dbPath.toFile(), "ds-descriptor.xml"));
        FeatureType[] effectiveFeatureTypes = Lucene2Simple.getEffectiveFeatureTypes(dsDescriptor);

//        QueryInterface db = new LucenePatchQuery(dbPath.toFile(), dsDescriptor, effectiveFeatureTypes);
        QueryInterface db = new SimplePatchQuery(dbPath.toFile(), effectiveFeatureTypes);

        System.out.println("db.class = " + db.getClass());
        System.out.println("db.numElems = " + db.getNumPatchesInDatabase());

        long t2 = System.currentTimeMillis();

        final int numRetrievedImages = 50;
        final int numRetrievedImagesMax = numRetrievedImages * 1000;

        int patchCounter = 0;
        while (patchCounter < numRetrievedImagesMax) {
            final Patch[] archivePatches = db.getRandomPatches(numRetrievedImages);
            patchCounter += archivePatches.length;
        }
        System.out.println("patchCounter = " + patchCounter);


//        Patch patch42 = db.getPatch(42);
//        System.out.println("patch42.getPatchName() = " + patch42.getPatchName());
//        System.out.println("patch42.getPatchX() = " + patch42.getPatchX());
//        System.out.println("patch42.getPatchY() = " + patch42.getPatchY());
//        System.out.println("patch42.getFeatureValues() = " + Arrays.toString(patch42.getFeatureValues()));

        long t3 = System.currentTimeMillis();

        long delta1 = t2 - t1;
        long delta2 = t3 - t2;
        System.out.println("open DB = " + delta1);
        System.out.println("read  = " + delta2);
    }

}
