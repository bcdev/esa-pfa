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

import org.esa.pfa.activelearning.ActiveLearning;
import org.esa.pfa.activelearning.ClassifierPersitable;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.fe.op.Patch;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by marcoz on 17.04.15.
 */
public class LocalClassifierService implements ClassifierService {

    private final Path storageDirectory;

    public LocalClassifierService(Path storageDirectory) {
        this.storageDirectory = storageDirectory;
    }

    @Override
    public void startTraining(Patch[] qeueryPatches) {


    }

    @Override
    public Patch[] getmostAmbigous(int numTrainingImages) {
        return new Patch[0];
    }

    @Override
    public void train(Patch[] labeledPatches) {

    }

    @Override
    public Patch[] getBestPatches() {
        return new Patch[0];
    }

    @Override
    public String[] list() {
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(storageDirectory, "*.xml")) {
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
        Classifier classifier = new Classifier(classifierName, applicationDescriptor, this);
        save(classifier);
        return classifier;
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
        return loadClassifier(classifierName);
    }

    @Override
    public void save(Classifier classifier) throws IOException {
        PFAApplicationDescriptor applicationDescriptor = classifier.getApplicationDescriptor();
        String classifierName = classifier.getName();
        int numTrainingImages = classifier.getNumTrainingImages();
        int numRetrievedImages = classifier.getNumRetrievedImages();
        ActiveLearning al = classifier.getActiveLearning();

        final ClassifierPersitable persitable = new ClassifierPersitable(applicationDescriptor.getName(), numTrainingImages, numRetrievedImages, al);
        Path classifierPath = getClassifierPath(classifierName);
        persitable.write(classifierPath.toFile());
    }

    private Classifier loadClassifier(String classifierName) throws IOException {
        Path classifierPath = getClassifierPath(classifierName);
        if (!Files.exists(classifierPath)) {
            throw new IllegalArgumentException("Classifier does not exist. " + classifierName);
        }
        final ClassifierPersitable persitable = ClassifierPersitable.read(classifierPath.toFile());
        String applicationName = persitable.getApplicationName();
        PFAApplicationDescriptor applicationDescriptor = PFAApplicationRegistry.getInstance().getDescriptor(applicationName);


        Classifier classifier = new Classifier(classifierName, applicationDescriptor, this);
        classifier.setNumTrainingImages(persitable.getNumTrainingImages());
        classifier.setNumRetrievedImages(persitable.getNumRetrievedImages());

        ActiveLearning activeLearning = classifier.getActiveLearning();
        int numIterations = persitable.getNumIterations();
        activeLearning.setModel(persitable.getModel(), numIterations);

        ClassifierPersitable.PatchInfo[] queryPatchInfo = persitable.getQueryPatchInfo();
        if (queryPatchInfo != null && queryPatchInfo.length > 0) {
            classifier.setQueryPatchInfo(queryPatchInfo);
        }
        ClassifierPersitable.PatchInfo[] trainingPatchInfo = persitable.getTrainingPatchInfo();
        if (trainingPatchInfo != null && trainingPatchInfo.length > 0) {
            classifier.setTrainingPatchInfo(trainingPatchInfo);
        }
        return classifier;
    }


    private Path getClassifierPath(String classifierName) {
        return storageDirectory.resolve(classifierName + ".xml");
    }
}
