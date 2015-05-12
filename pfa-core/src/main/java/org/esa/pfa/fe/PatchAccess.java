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

import org.esa.pfa.fe.op.Patch;
import org.esa.snap.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service for accessing patches.
 */
public class PatchAccess {

    private final File patchRootDir;

    public PatchAccess(File patchRootDir) {
        this.patchRootDir = patchRootDir;
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

    public Path findFexPath(String productName) throws IOException {
        // check for directory
        File fexDir = new File(patchRootDir, productName + ".fex");
        File fezFile = new File(patchRootDir, productName + ".fex.zip");
        if (fexDir.isDirectory()) {
            return fexDir.toPath();
        } else if (fezFile.isFile()) {
            URI zipUri = URI.create("jar:file:" + fezFile.toURI().getPath() + "!/");
            Path entryZip = FileUtils.getPathFromURI(zipUri);
            return entryZip.resolve(productName + ".fex");
        }
        throw new IOException("Could not load patch for: " + productName);
    }

    private Path findPatchPath(String productName, int patchX, int patchY) throws IOException {
        // check for directory
        File fexDir = new File(patchRootDir, productName + ".fex");
        File fezFile = new File(patchRootDir, productName + ".fex.zip");

        Path fexPath = null;
        if (fexDir.isDirectory()) {
            fexPath = fexDir.toPath();
        } else if (fezFile.isFile()) {
            URI zipUri = URI.create("jar:file:" + fezFile.toURI().getPath() + "!/");
            Path entryZip = FileUtils.getPathFromURI(zipUri);
            fexPath = entryZip.resolve(productName + ".fex");
        }
        if (fexPath != null) {
            Path patchPath = fexPath.resolve(String.format("x%03dy%03d", patchX, patchY));
            if (Files.exists(patchPath)) {
                return patchPath;
            }
            patchPath = fexPath.resolve(String.format("x%02dy%02d", patchX, patchY));
            if (Files.exists(patchPath)) {
                return patchPath;
            }
        }

        throw new IOException("Could not load patch for: " + productName + "  x: " + patchX + "  y:" + patchY);
    }
}
