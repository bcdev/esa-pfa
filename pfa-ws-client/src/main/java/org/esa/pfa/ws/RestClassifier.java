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

package org.esa.pfa.ws;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.pfa.classifier.Classifier;
import org.esa.pfa.classifier.ClassifierModel;
import org.esa.pfa.fe.op.Patch;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * A REST based implementation
 */
public class RestClassifier implements Classifier {


    private final String classifierName;
    private ClassifierModel model;
    private final WebTarget target;


    public RestClassifier(String classifierName, ClassifierModel model, WebTarget target) {
        this.classifierName = classifierName;
        this.model = model;
        this.target = target;
    }

    @Override
    public String getName() {
        return classifierName;
    }

    @Override
    public int getNumTrainingImages() {
        return model.getNumTrainingImages();
    }

    @Override
    public void setNumTrainingImages(int numTrainingImages) {
        model.setNumTrainingImages(numTrainingImages);

        target.path("classifiers").path(classifierName).path("setNumTrainingImages").
                queryParam("value", numTrainingImages).
                request().
                post(Entity.entity("dummy", MediaType.TEXT_PLAIN));
    }

    @Override
    public int getNumRetrievedImages() {
        return model.getNumRetrievedImages();
    }

    @Override
    public void setNumRetrievedImages(int numRetrievedImages) {
        model.setNumRetrievedImages(numRetrievedImages);

        target.path("classifiers").path(classifierName).path("setNumRetrievedImages").
                queryParam("value", numRetrievedImages).
                request().
                post(Entity.entity("dummy", MediaType.TEXT_PLAIN));

    }

    @Override
    public int getNumIterations() {
        return model.getNumIterations();
    }


    @Override
    public Patch[] startTraining(Patch[] queryPatches, ProgressMonitor pm) throws IOException {
        RestTransferValue query = new RestTransferValue();
        query.setPatches(queryPatches);
        Form form = new Form();
        form.param("queryPatches", query.toXML());

        String resultXML = target.path("classifiers").path(classifierName).path("startTraining").
                request().
                post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE)).
                readEntity(String.class);

        RestTransferValue response = RestTransferValue.fromXML(resultXML);
        model.setNumIterations(response.getNumIterations());
        return response.getPatches();
    }

    @Override
    public Patch[] trainAndClassify(boolean prePopulate, Patch[] labeledPatches, ProgressMonitor pm) throws IOException {
        RestTransferValue query = new RestTransferValue();
        query.setPatches(labeledPatches);
        Form form = new Form();
        form.param("labeledPatches", query.toXML());
        form.param("prePopulate", Boolean.toString(prePopulate));

        String resultXML = target.path("classifiers").path(classifierName).path("trainAndClassify").
                request().
                post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE)).
                readEntity(String.class);

        RestTransferValue response = RestTransferValue.fromXML(resultXML);
        model.setNumIterations(response.getNumIterations());
        return response.getPatches();
    }

    @Override
    public Patch[] getMostAmbigous(boolean prePopulate, ProgressMonitor pm) throws IOException {
        Form form = new Form();
        form.param("prePopulate", Boolean.toString(prePopulate));

        String resultXML = target.path("classifiers").path(classifierName).path("getMostAmbigous").
                request().
                post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE)).
                readEntity(String.class);

        RestTransferValue response = RestTransferValue.fromXML(resultXML);
        model.setNumIterations(response.getNumIterations());
        return response.getPatches();
    }


    @Override
    public URI getPatchQuicklookUri(Patch patch, String quicklookBandName) throws IOException {
        return getPatchQuicklookTarget(patch, quicklookBandName).getUri();
    }

    @Override
    public BufferedImage getPatchQuicklook(Patch patch, String quicklookBandName) throws IOException {
        Response response = getPatchQuicklookTarget(patch, quicklookBandName).request().get();
        if (response.getStatusInfo().getStatusCode() == Response.Status.OK.getStatusCode()) {
            return response.readEntity(BufferedImage.class);
        } else {
            return null;
        }
    }

    private WebTarget getPatchQuicklookTarget(Patch patch, String quicklookBandName) {
        String parentProductName = patch.getParentProductName();
        String patchX = Integer.toString(patch.getPatchX());
        String patchY = Integer.toString(patch.getPatchY());
        return target.path("quicklook")
                .path(parentProductName)
                .path(patchX)
                .path(patchY)
                .path(quicklookBandName);
    }

    @Override
    public File getPatchProductFile(Patch patch) throws IOException {
        return null; // TODO
    }

    @Override
    public String getFeaturesAsText(Patch patch) throws IOException {
        String parentProductName = patch.getParentProductName();
        String patchX = Integer.toString(patch.getPatchX());
        String patchY = Integer.toString(patch.getPatchY());
        final Response response = target.path("features")
                .path(parentProductName)
                .path(patchX)
                .path(patchY)
                .request().get();
        return response.readEntity(String.class);
    }

}
