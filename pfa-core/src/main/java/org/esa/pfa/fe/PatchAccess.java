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
import sun.nio.ch.IOUtil;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for accessing patches.
 */
public class PatchAccess {

    private final FeatureType[] effectiveFeatureTypes;
    private final File patchRootDir;

    public PatchAccess(File patchRootDir, FeatureType[] effectiveFeatureTypes) {
        this.patchRootDir = patchRootDir;
        this.effectiveFeatureTypes = effectiveFeatureTypes;
    }

    public String getFeaturesAsText(String parentProductName, int patchX, int patchY) throws IOException {
        Path patchPath = findPatchPath(parentProductName, patchX, patchY);
        final Path featuresTxt = patchPath.resolve("features.txt");
        if (Files.exists(featuresTxt)) {
            return new String(Files.readAllBytes(featuresTxt));
        }
        return "";
    }

    public Path getPatchImagePath(String parentProductName, int patchX, int patchY, String patchImageFileName) throws IOException {
        Path patchPath = findPatchPath(parentProductName, patchX, patchY);
        Path patchImagePath = patchPath.resolve(patchImageFileName);
        if (Files.exists(patchPath)) {
            return patchImagePath;
        }
        return null;
    }

    public File getPatchProductFile(Patch patch) throws IOException {
        Path patchPath = findPatchPath(patch.getParentProductName(), patch.getPatchX(), patch.getPatchY());
        final File patchProductFile = patchPath.resolve("patch.dim").toFile();
        if (!patchProductFile.exists()) {
            return null;
        }
        return patchProductFile;
    }

    private Path findPatchPath(String productName, int patchX, int patchY) throws IOException {
        // check for directory
        File fexDir = new File(patchRootDir, productName + ".fex");
        File fezFile = new File(patchRootDir, productName + ".fex.zip");
        if (fexDir.isDirectory()) {
            File[] patchFiles = fexDir.listFiles((dir, name) -> name.matches("x0*" + patchX + "y0*" + patchY));
            if (patchFiles != null && patchFiles.length == 1) {
                return patchFiles[0].toPath();
            }
        } else if (fezFile.isFile()) {
            // TODO read from zip
            throw new UnsupportedOperationException("Reading patch from zip not implemented!!");
        }
        throw new IOException("Could not load patch for: " + productName + "  x: " + patchX + "  y:" + patchY);
    }
}
