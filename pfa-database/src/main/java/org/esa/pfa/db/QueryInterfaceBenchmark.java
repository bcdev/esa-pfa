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
 * A benchmark for differebnt {@link QueryInterface QueryInterfaces}.
 *
 * @author Marco Zuehlke
 */
public class QueryInterfaceBenchmark {

    private static final int NUM_RETRIEVED_IMAGES = 50;
    private static final int NUM_RETRIEVED_IMAGES_MAX = NUM_RETRIEVED_IMAGES * 100;

    public static void main(String[] args) throws IOException {
        Path dbPath = Paths.get(args[0]);
        System.out.println("dbPath = " + dbPath);

        long t1 = System.currentTimeMillis();
        DatasetDescriptor dsDescriptor = DatasetDescriptor.read(new File(dbPath.toFile(), "ds-descriptor.xml"));
        FeatureType[] effectiveFeatureTypes = Lucene2Simple.getEffectiveFeatureTypes(dsDescriptor);

        long t2 = System.currentTimeMillis();
        long delta1 = t2 - t1;
        System.out.println("prepare = " + delta1);

        QueryInterface db;
        try {
            db = new SimplePatchQuery(dbPath.toFile(), effectiveFeatureTypes);
            queryDB(t2, db);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        t2 = System.currentTimeMillis();
        try {
            db = new LucenePatchQuery(dbPath.toFile(), dsDescriptor, effectiveFeatureTypes);
            queryDB(t2, db);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void queryDB(long t2, QueryInterface db) throws IOException {
        System.out.println("db.class = " + db.getClass());

        long t3 = System.currentTimeMillis();
        queryDB(db);
        long t4 = System.currentTimeMillis();


        long delta2 = t3 - t2;
        long delta3 = t4 - t3;

        System.out.println("open DB = " + delta2);
        System.out.println("read    = " + delta3);
    }

    private static void queryDB(QueryInterface db) throws IOException {

        int patchCounter = 0;
        while (patchCounter < NUM_RETRIEVED_IMAGES_MAX) {
            final Patch[] archivePatches = db.getRandomPatches(NUM_RETRIEVED_IMAGES);
            patchCounter += archivePatches.length;
        }
    }

}
