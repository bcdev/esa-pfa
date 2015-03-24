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

package org.esa.pfa.fe;

import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

/**
 * Created by marcoz on 23.03.15.
 */
public class PatchAccess {

    private final FeatureType[] effectiveFeatureTypes;
    private final File patchRootDir;

    public PatchAccess(File patchRootDir, FeatureType[] effectiveFeatureTypes) {
        this.patchRootDir = patchRootDir;
        this.effectiveFeatureTypes = effectiveFeatureTypes;
    }

    public Patch loadPatch(String parentProductName, int patchX, int patchY, int label) throws IOException {
        File patchFile = findPatch(parentProductName, patchX, patchY);
        if (patchFile != null) {
            final Patch patch = new Patch(parentProductName, patchX, patchY, null, null);
            patch.setLabel(label);
            readPatchFeatures(patch, patchFile);
            return patch;
        }
        throw new IOException("Could not load patch for: "+ parentProductName);
    }

    public void readPatchFeatures(Patch patch, File patchFile) throws IOException {
        final File featureFile = new File(patchFile, "features.txt");
        patch.readFeatureFile(featureFile, effectiveFeatureTypes);
    }

    public String[] getAvailableQuickLooks(final Patch patch) throws IOException {
        File patchFile = findPatch(patch.getParentProductName(), patch.getPatchX(), patch.getPatchY());
        final File[] imageFiles = patchFile.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isFile() && file.getName().toLowerCase().endsWith(".png");
            }
        });
        if (imageFiles == null) {
            throw new IOException("No patch image found in " + patchFile);
        }
        final String[] quicklookFilenames = new String[imageFiles.length];
        int i = 0;
        for (File imageFile : imageFiles) {
            quicklookFilenames[i++] = imageFile.getName();
        }
        return quicklookFilenames;
    }

    public URL retrievePatchImage(final Patch patch, final String patchImageFileName) throws IOException {
        File patchFile = findPatch(patch.getParentProductName(), patch.getPatchX(), patch.getPatchY());

        File imageFile;
        if (patchImageFileName != null && !patchImageFileName.isEmpty()) {
            imageFile = new File(patchFile, patchImageFileName);
        } else {
            final String[] quicklookFilenames = getAvailableQuickLooks(patch);
            imageFile = new File(patchFile, quicklookFilenames[0]);
        }

        return new URL("file:" + imageFile.getAbsolutePath());
    }

    public File getPatchProductFile(Patch patch) throws IOException {
        File patchFile = findPatch(patch.getParentProductName(), patch.getPatchX(), patch.getPatchY());
        if (patchFile == null) {
            return null;
        }

        final File patchProductFile = new File(patchFile, "patch.dim");
        if (!patchProductFile.exists()) {
            return null;
        }
        return patchProductFile;
    }

    public File findPatch(String productName, int patchX, int patchY) throws IOException {
        // check for directory
        File fexDir = new File(patchRootDir, productName + ".fex");
        File fezFile = new File(patchRootDir, productName + ".fex.zip");
        if (fexDir.isDirectory()) {
            File[] patchFiles = fexDir.listFiles((dir, name) -> name.matches("x0*" + patchX + "y0*" + patchY));
            if (patchFiles != null && patchFiles.length == 1) {
                return patchFiles[0];
            }
        } else if (fezFile.isFile()) {
            // TODO read from zip
            throw new UnsupportedOperationException("Reading patch from zip not implemented!!");
        }
        throw new IOException("Could not load patch for: " + productName + "  x: " + patchX + "  y:" + patchY);
    }
}
