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

import com.thoughtworks.xstream.XStream;
import org.esa.pfa.activelearning.SvmModelReference;
import org.esa.pfa.fe.op.Patch;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * The data model for the {@link Classifier}.
 * It can be serializes to XML, and restore from XML.
 */
public class ClassifierModel {

    private static final int NUM_TRAINING_IMAGES_DEFAULT = 12;
    private static final int NUM_RETRIEVED_IMAGES_DEFAULT = 50;
    private static final int NUM_RETRIEVED_IMAGES_MAX_DEFAULT = 100 * NUM_RETRIEVED_IMAGES_DEFAULT;
    private static final int NUM_RANDOM_IMAGES_DEFAULT = 500;

    private String applicationName;
    private int numTrainingImages = NUM_TRAINING_IMAGES_DEFAULT;
    private int numRetrievedImages = NUM_RETRIEVED_IMAGES_DEFAULT;
    private int numRetrievedImagesMax = NUM_RETRIEVED_IMAGES_MAX_DEFAULT;
    private int numRandomImages = NUM_RANDOM_IMAGES_DEFAULT;
    private int numIterations;
    private SvmModelReference svmModelReference;

    private List<Patch> testData = new ArrayList<>();
    private List<Patch> queryData = new ArrayList<>();
    private List<Patch> trainingData = new ArrayList<>();

    private ClassifierModel() {
    }

    ClassifierModel(String applicationName) {
        this.applicationName = applicationName;
        this.svmModelReference = new SvmModelReference();
    }

    public String getApplicationName() {
        return applicationName;
    }

    public int getNumTrainingImages() {
        return numTrainingImages;
    }

    public void setNumTrainingImages(int numTrainingImages) {
        this.numTrainingImages = numTrainingImages;
    }

    public int getNumRetrievedImages() {
        return numRetrievedImages;
    }

    public void setNumRetrievedImages(int numRetrievedImages) {
        this.numRetrievedImages = numRetrievedImages;
    }

    public int getNumRetrievedImagesMax() {
        return numRetrievedImagesMax;
    }

    public void setNumRetrievedImagesMax(int numRetrievedImagesMax) {
        this.numRetrievedImagesMax = numRetrievedImagesMax;
    }

    public int getNumRandomImages() {
        return numRandomImages;
    }

    public void setNumRandomImages(int numRandomImages) {
        this.numRandomImages = numRandomImages;
    }

    public int getNumIterations() {
        return numIterations;
    }

    public void setNumIterations(int numIterations) {
        this.numIterations = numIterations;
    }

    public SvmModelReference getSvmModelReference() {
        return svmModelReference;
    }

    public List<Patch> getTestData() {
        return testData;
    }

    public List<Patch> getQueryData() {
        return queryData;
    }

    public List<Patch> getTrainingData() {
        return trainingData;
    }

    public static ClassifierModel fromXML(String xml) {
        ClassifierModel model = new ClassifierModel();
        getXStream().fromXML(xml, model);
        return model;
    }

    public String toXML() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            getXStream().toXML(this, writer);
            return writer.toString();
        }
    }

    public static ClassifierModel fromFile(final File file) throws IOException {
        try (Reader reader = new FileReader(file)) {
            ClassifierModel model = new ClassifierModel();
            getXStream().fromXML(reader, model);
            return model;
        }
    }

    public void toFile(final File classifierFile) throws IOException {
        try (FileWriter fileWriter = new FileWriter(classifierFile)) {
            getXStream().toXML(this, fileWriter);
        } catch (IOException e) {
            throw new IOException("Unable to write " + classifierFile.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    private static XStream getXStream() {
        XStream xStream = new XStream();
        xStream.alias("classifier", ClassifierModel.class);
        xStream.alias("patch", Patch.class);
        xStream.omitField(Patch.class, "featureList");
        xStream.omitField(Patch.class, "imageMap");
        xStream.omitField(Patch.class, "listenerList");
        xStream.omitField(Patch.class, "patchProduct");
        xStream.setClassLoader(ClassifierModel.class.getClassLoader());
        return xStream;
    }

}
