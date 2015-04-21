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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A local implementation for the classifier
 */
public class LocalClassifierManager implements ClassifierManager {

    private final Path classifierStoragePath;
    private final Path patchPath;
    private final Path dbPath;

    public LocalClassifierManager(Path classifierStoragePath, Path dbPath, Path patchPath) {
        this.classifierStoragePath = classifierStoragePath;
        this.dbPath = dbPath;
        this.patchPath = patchPath;
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
    public Classifier create(String classifierName, String applicationName) throws IOException {
        PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
        PFAApplicationDescriptor applicationDescriptor = applicationRegistry.getDescriptor(applicationName);
        Path classifierPath = getClassifierPath(classifierStoragePath, classifierName);
        RealLocalClassifier realLocalClassifier = new RealLocalClassifier(classifierPath, applicationDescriptor, patchPath, dbPath);
        realLocalClassifier.saveClassifier();
        return new Classifier(classifierName, applicationDescriptor, realLocalClassifier);
    }

    @Override
    public void delete(String classifierName) throws IOException {
        Path classifierPath = getClassifierPath(classifierStoragePath, classifierName);
        if (Files.exists(classifierPath)) {
            Files.delete(classifierPath);
        }
    }

    @Override
    public Classifier get(String classifierName) throws IOException {
        Path classifierPath = getClassifierPath(classifierStoragePath, classifierName);
        return RealLocalClassifier.loadClassifier(classifierName, classifierPath, patchPath, dbPath);
    }

    public static Path getClassifierPath(Path storageDirectory, String classifierName) {
        return storageDirectory.resolve(classifierName + ".xml");
    }

}
