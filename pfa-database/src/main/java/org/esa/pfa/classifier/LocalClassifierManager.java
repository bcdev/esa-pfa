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
import org.esa.pfa.fe.op.DatasetDescriptor;

import java.io.File;
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
    private final String[] applicationDatabases;

    private String appDBId;
    private Path classifierStoragePath;
    private Path patchPath;
    private Path dbPath;

    public LocalClassifierManager(final URI uri) throws IOException {
        this.uri = uri;
        final Path auxPath = Paths.get(uri);

        // look for dataset descriptors
        if (Files.exists(auxPath.resolve("ds-descriptor.xml"))) {
            DatasetDescriptor dsDescriptor = DatasetDescriptor.read(new File(auxPath.toFile(), "ds-descriptor.xml"));
            applicationDatabases = new String[] { "" };
        } else {
            final List<String> appList = new ArrayList<>();
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(auxPath)) {
                for (Path path : directoryStream) {
                    if(Files.isDirectory(path)) {
                        if (Files.exists(path.resolve("ds-descriptor.xml"))) {
                            appList.add(path.getFileName().toString());
                        }
                    }
                }
            }
            applicationDatabases = appList.toArray(new String[appList.size()]);
        }
    }

    @Override
    public String[] listApplicationDatabases() {
        return applicationDatabases;
    }

    @Override
    public void selectApplicationDatabase(final String appDBId) throws IOException {
        this.appDBId = appDBId;

        dbPath = Paths.get(uri).resolve(appDBId);
        patchPath = dbPath;

        classifierStoragePath = dbPath.resolve("Classifiers");
        if (!Files.exists(classifierStoragePath)) {
            Files.createDirectories(classifierStoragePath);
        }
    }

    @Override
    public String getApplicationDatabase() {
        return appDBId;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public String getApplication() throws IOException {
        DatasetDescriptor dsDescriptor = DatasetDescriptor.read(new File(dbPath.toFile(), "ds-descriptor.xml"));
        return dsDescriptor.getName();
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
        PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
        PFAApplicationDescriptor applicationDescriptor = applicationRegistry.getDescriptorById(getApplication());
        if(applicationDescriptor == null) {
            throw new IOException("Unknown application id "+getApplication());
        }
        Path classifierPath = getClassifierPath(classifierName);
        ClassifierModel classifierModel = new ClassifierModel(applicationDescriptor.getName());
        LocalClassifier localClassifier = new LocalClassifier(classifierName, classifierModel, classifierPath, applicationDescriptor, patchPath, dbPath);
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
    public Classifier get(String classifierName) throws IOException {
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
