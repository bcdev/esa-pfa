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

import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * @author marcoz
 */
public class SimplePatchQuery implements QueryInterface {

    public static final String FEATURE_DB = "simple.DB.features";
    public static final String NAME_DB = "simple.DB.productnames";

    private final FeatureType[] effectiveFeatureTypes;
    private final String[] productNames;
    private final FileChannel fileChannel;
    private final int entrySize;
    private final int numElems;
    private final ByteBuffer bb;

    public SimplePatchQuery(File datasetDir, FeatureType[] effectiveFeatureTypes) throws IOException {
        this.effectiveFeatureTypes = effectiveFeatureTypes;
        productNames = readProductNames(new File(datasetDir, NAME_DB));
        FileInputStream fileInputStream = new FileInputStream(new File(datasetDir, FEATURE_DB));
        fileChannel = fileInputStream.getChannel();
        numElems = new DataInputStream(fileInputStream).readInt();
        entrySize = Integer.BYTES + Integer.BYTES + Integer.BYTES + getFeatureSize();
        bb = ByteBuffer.allocateDirect(entrySize);
    }

    @Override
    public int getNumPatchesInDatabase() {
        return numElems;
    }

    @Override
    public Patch getPatch(int patchIndex) throws IOException {
        return readPatch(patchIndex);
    }

    @Override
    public Patch[] getRandomPatches(int numPatches) throws IOException {

        final List<Patch> patchList = new ArrayList<>(numPatches);
        IntStream randomInts = new Random().ints(numPatches, 0, numElems);
        randomInts.forEach(value -> {
            try {
                Patch patch = readPatch(value);
                patchList.add(patch);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return patchList.toArray(new Patch[patchList.size()]);
    }

    private Patch readPatch(int value) throws IOException {
        bb.rewind();
        int position = 4 + value * entrySize;
        fileChannel.read(bb, position);
        bb.rewind();

        int productIndex = bb.getInt();
        String productName = productNames[productIndex];

        int patchX = bb.getInt();
        int patchY = bb.getInt();
        Patch patch = new Patch(productName, patchX, patchY);

        for (FeatureType feaType : effectiveFeatureTypes) {
            final Class<?> valueType = feaType.getValueType();
            Feature feature = null;
            if (Double.class.isAssignableFrom(valueType)) {
                feature = new Feature(feaType, bb.getDouble());
            } else if (Integer.class.isAssignableFrom(valueType)) {
                feature = new Feature(feaType, bb.getInt());
            }
            patch.addFeature(feature);
        }
        return patch;
    }

    private int getFeatureSize() {
        int entrySize = 0;
        for (FeatureType feaType : effectiveFeatureTypes) {
            final Class<?> valueType = feaType.getValueType();
            if (Double.class.isAssignableFrom(valueType)) {
                entrySize += Double.BYTES;
            } else if (Integer.class.isAssignableFrom(valueType)) {
                entrySize += Integer.BYTES;
            } else {
                throw new IllegalArgumentException("valueType '" + valueType + "' not supported (yet).");
            }
        }
        System.out.println("entrySize = " + entrySize);
        return entrySize;
    }

    private static String[] readProductNames(File file) throws IOException {
        try (
                DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))
        ) {
            int numNames = dis.readInt();
            String[] productNames = new String[numNames];
            for (int i = 0; i < productNames.length; i++) {
                productNames[i] = dis.readUTF();
            }
            return productNames;
        }
    }
}
