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

package org.esa.pfa.classifier;

import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.fe.PatchAccess;
import org.esa.pfa.fe.op.DatasetDescriptor;
import org.esa.pfa.fe.op.Patch;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A local implementation for the classifier
 */
public class LocalClassifierManager implements ClassifierManager {

    private final Path dbPath;
    private final Path classifierStoragePath;
    private final PatchAccess patchAccess;
    private final PFAApplicationDescriptor appDescriptor;

    public LocalClassifierManager(Path dbPath) throws IOException {
        this.dbPath = dbPath;
        this.classifierStoragePath = dbPath.resolve("Classifiers");
        if (!Files.exists(classifierStoragePath)) {
            Files.createDirectories(classifierStoragePath);
        }
        DatasetDescriptor dsDescriptor = DatasetDescriptor.read(new File(dbPath.toFile(), "ds-descriptor.xml"));
        String appName = dsDescriptor.getName();
        this.appDescriptor = PFAApplicationRegistry.getInstance().getDescriptorByName(appName);
        if (appDescriptor == null) {
            throw new IOException("Unknown application name " + appName);
        }
        this.patchAccess = new PatchAccess(dbPath, appDescriptor.getProductNameResolver());
    }

    @Override
    public String getApplicationId() {
        return appDescriptor.getId();
    }

    @Override
    public String[] list() {
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(classifierStoragePath, "*.xml")) {
            List<String> names = new ArrayList<>();
            directoryStream.forEach(path -> {
                String name = path.toFile().getName();
                int lastDotPosition = name.lastIndexOf(".");
                names.add(name.substring(0, lastDotPosition));
            });
            return names.toArray(new String[names.size()]);
        } catch (IOException e) {
            e.printStackTrace();
            return new String[0];
        }
    }

    @Override
    public Classifier create(String classifierName) throws IOException {
        Path classifierPath = getClassifierPath(classifierName);
        ClassifierModel classifierModel = new ClassifierModel(appDescriptor.getName());
        LocalClassifier localClassifier = new LocalClassifier(classifierName, classifierModel, classifierPath, appDescriptor, dbPath);
        localClassifier.saveClassifier();
        return localClassifier;
    }

    @Override
    public void delete(String classifierName) throws IOException {
        Path classifierPath = getClassifierPath(classifierName);
        if (Files.exists(classifierPath)) {
            Files.delete(classifierPath);
        }
    }

    @Override
    public LocalClassifier get(String classifierName) throws IOException {
        Path classifierPath = getClassifierPath(classifierName);
        return LocalClassifier.loadClassifier(classifierName, classifierPath, dbPath);
    }

    @Override
    public URI getPatchQuicklookUri(Patch patch, String quicklookBandName) throws IOException {
        Path patchImagePath = patchAccess.getPatchImagePath(patch.getParentProductName(), patch.getPatchX(), patch.getPatchY(), quicklookBandName);
        if (patchImagePath != null) {
            return patchImagePath.toUri();
        } else {
            return null;
        }
    }

    @Override
    public BufferedImage getPatchQuicklook(Patch patch, String quicklookBandName) throws IOException {
        Path patchImagePath = patchAccess.getPatchImagePath(patch.getParentProductName(), patch.getPatchX(), patch.getPatchY(), quicklookBandName);
        if (patchImagePath != null) {
            return ImageIO.read(patchImagePath.toUri().toURL());
        } else {
            return null;
        }
    }

    @Override
    public File getPatchProductFile(Patch patch) throws IOException {
        return patchAccess.getPatchProductFile(patch);
    }

    @Override
    public String getFeaturesAsText(Patch patch) throws IOException {
        return patchAccess.getFeaturesAsText(patch.getParentProductName(), patch.getPatchX(), patch.getPatchY());
    }

    @Override
    public URI getFexOverviewUri(Patch patch) {
        return null; // not supported in local mode
    }

    public PatchAccess getPatchAccess() {
        return patchAccess;
    }

    public Path getClassifierPath(String classifierName) {
        return classifierStoragePath.resolve(classifierName + ".xml");
    }

}
