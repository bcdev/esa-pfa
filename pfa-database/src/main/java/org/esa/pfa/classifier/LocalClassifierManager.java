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

import org.esa.pfa.db.DsIndexerTool;
import org.esa.pfa.db.LucenePatchQuery;
import org.esa.pfa.db.QueryInterface;
import org.esa.pfa.db.SimplePatchQuery;
import org.esa.pfa.fe.AbstractApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.fe.PatchAccess;
import org.esa.pfa.fe.op.DatasetDescriptor;
import org.esa.pfa.fe.op.FeatureType;
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
import java.util.Set;

/**
 * A local implementation for the classifier
 */
public class LocalClassifierManager implements ClassifierManager {

    private final Path classifierStoragePath;
    private final PatchAccess patchAccess;
    private final PFAApplicationDescriptor applicationDescriptor;
    private final String applicationName;
    private final QueryInterface queryInterface;
    private final String databaseName;

    LocalClassifierManager(String databaseName, Path dbPath) throws IOException {
        this.databaseName = databaseName;
        this.classifierStoragePath = dbPath.resolve("Classifiers");
        if (!Files.exists(classifierStoragePath)) {
            Files.createDirectories(classifierStoragePath);
        }
        DatasetDescriptor datasetDescriptor = DatasetDescriptor.read(new File(dbPath.toFile(), "ds-descriptor.xml"));
        applicationName = datasetDescriptor.getName();
        this.applicationDescriptor = PFAApplicationRegistry.getInstance().getDescriptorByName(applicationName);
        if (applicationDescriptor == null) {
            throw new IOException("Unknown application name " + applicationName);
        }
        Set<String> defaultFeatureSet = applicationDescriptor.getDefaultFeatureSet();
        FeatureType[] featureTypes = datasetDescriptor.getFeatureTypes();
        FeatureType[] effectiveFeatureTypes = AbstractApplicationDescriptor.getEffectiveFeatureTypes(featureTypes, defaultFeatureSet);
        patchAccess = new PatchAccess(dbPath, applicationDescriptor.getProductNameResolver());
        if (Files.exists(dbPath.resolve(SimplePatchQuery.NAME_DB)) && Files.exists(dbPath.resolve(SimplePatchQuery.FEATURE_DB))) {
            queryInterface = new SimplePatchQuery(dbPath.toFile(), effectiveFeatureTypes);
        } else if (Files.exists(dbPath.resolve(DsIndexerTool.DEFAULT_INDEX_NAME))) {
            queryInterface = new LucenePatchQuery(dbPath.toFile(), datasetDescriptor, effectiveFeatureTypes);
        } else {
            queryInterface = null; // tests only
        }
    }

    @Override
    public String getApplicationId() {
        return applicationDescriptor.getId();
    }

    @Override
    public String getDatabaseName() {
        return databaseName;
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
        ClassifierModel classifierModel = new ClassifierModel(applicationName);
        LocalClassifier localClassifier = new LocalClassifier(classifierName, classifierModel, classifierPath, queryInterface);
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
        if (!Files.exists(classifierPath)) {
            throw new IllegalArgumentException("Classifier does not exist. " + classifierName);
        }
        ClassifierModel classifierModel = ClassifierModel.fromFile(classifierPath.toFile());
        return new LocalClassifier(classifierName, classifierModel, classifierPath, queryInterface);
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
