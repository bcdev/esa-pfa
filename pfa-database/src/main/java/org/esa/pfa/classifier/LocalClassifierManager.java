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
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * A local implementation for the classifier
 */
public class LocalClassifierManager implements ClassifierManager {

    private final URI uri;
    private final String appId;
    private final Path classifierStoragePath;
    private final Path patchPath;
    private final Path dbPath;

    public LocalClassifierManager(URI uri, String appId) throws IOException {
        this.uri = uri;
        this.appId = appId;
        Path auxPath = Paths.get(uri);
        classifierStoragePath = auxPath.resolve("Classifiers");
        if (!Files.exists(classifierStoragePath)) {
            Files.createDirectories(classifierStoragePath);
        }
        patchPath = auxPath;
        dbPath = auxPath;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public String getApplicationId() {
        return appId;
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
    public ClassifierDelegate create(String classifierName) throws IOException {
        PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
        PFAApplicationDescriptor applicationDescriptor = applicationRegistry.getDescriptorById(appId);
        Path classifierPath = getClassifierPath(classifierName);
        ClassifierModel classifierModel = new ClassifierModel(applicationDescriptor.getName());
        LocalClassifier realLocalClassifier = new LocalClassifier(classifierModel, classifierPath, applicationDescriptor, patchPath, dbPath);
        realLocalClassifier.saveClassifier();
        return new ClassifierDelegate(classifierName, applicationDescriptor, realLocalClassifier);
    }

    @Override
    public void delete(String classifierName) throws IOException {
        Path classifierPath = getClassifierPath(classifierName);
        if (Files.exists(classifierPath)) {
            Files.delete(classifierPath);
        }
    }

    @Override
    public ClassifierDelegate get(String classifierName) throws IOException {
        Path classifierPath = getClassifierPath(classifierName);
        return LocalClassifier.loadClassifier(classifierName, classifierPath, patchPath, dbPath);
    }

    public Path getPatchPath() {
        return patchPath;
    }

    public Path getDbPath() {
        return dbPath;
    }

    public Path getClassifierPath(String classifierName) {
        return classifierStoragePath.resolve(classifierName + ".xml");
    }

}
