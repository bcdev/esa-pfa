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
import org.esa.pfa.classifier.ClassifierStats;
import org.esa.pfa.fe.op.Patch;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

/**
 * A REST based implementation
 */
class RemoteClassifier implements Classifier {


    private final String classifierName;
    private final WebTarget target;


    RemoteClassifier(String classifierName, WebTarget target) {
        this.classifierName = classifierName;
        this.target = target;
    }

    @Override
    public String getName() {
        return classifierName;
    }

    @Override
    public void setNumTrainingImages(int numTrainingImages) {
        setIntValue("setNumTrainingImages", numTrainingImages);
    }

    @Override
    public void setNumRetrievedImages(int numRetrievedImages) {
        setIntValue("setNumRetrievedImages", numRetrievedImages);
    }

    @Override
    public void setNumRetrievedImagesMax(int numRetrievedImagesMax) {
        setIntValue("setNumRetrievedImagesMax", numRetrievedImagesMax);
    }

    @Override
    public void setNumRandomImages(int numRandomImages) {
        setIntValue("setNumRandomImages", numRandomImages);
    }

    private void setIntValue(String parameter, int value) {
        target.path(parameter).
                queryParam("value", value).
                request().
                post(Entity.entity("dummy", MediaType.TEXT_PLAIN));
    }

    @Override
    public ClassifierStats getClassifierStats() {
        Response response = target.path("getClassifierStats").request().get();
        String classifierStateValues = response.readEntity(String.class);
        String[] values = classifierStateValues.split(" ");
        if (values.length != 9) {
            return new ClassifierStats(0, 0, 0, 0, 0, 0, 0, 0, 0);
        } else {
            int i0 = Integer.parseInt(values[0]);
            int i1 = Integer.parseInt(values[1]);
            int i2 = Integer.parseInt(values[2]);
            int i3 = Integer.parseInt(values[3]);
            int i4 = Integer.parseInt(values[4]);
            int i5 = Integer.parseInt(values[5]);
            int i6 = Integer.parseInt(values[6]);
            int i7 = Integer.parseInt(values[7]);
            int i8 = Integer.parseInt(values[8]);
            return new ClassifierStats(i0, i1, i2, i3, i4, i5, i6, i7, i8);
        }
    }

    @Override
    public Patch[] startTraining(Patch[] queryPatches, ProgressMonitor pm) throws IOException {
        RestTransferValue query = new RestTransferValue();
        query.setPatches(queryPatches);
        Form form = new Form();
        form.param("queryPatches", query.toXML());

        String resultXML = target.path("startTraining").
                request().
                post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE)).
                readEntity(String.class);

        RestTransferValue response = RestTransferValue.fromXML(resultXML);
        return response.getPatches();
    }

    @Override
    public Patch[] trainAndClassify(boolean prePopulate, Patch[] labeledPatches, ProgressMonitor pm) throws IOException {
        RestTransferValue query = new RestTransferValue();
        query.setPatches(labeledPatches);
        Form form = new Form();
        form.param("labeledPatches", query.toXML());
        form.param("prePopulate", Boolean.toString(prePopulate));

        String resultXML = target.path("trainAndClassify").
                request().
                post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE)).
                readEntity(String.class);

        RestTransferValue response = RestTransferValue.fromXML(resultXML);
        return response.getPatches();
    }

    @Override
    public Patch[] getMostAmbigous(boolean prePopulate, ProgressMonitor pm) throws IOException {
        Form form = new Form();
        form.param("prePopulate", Boolean.toString(prePopulate));

        String resultXML = target.path("getMostAmbigous").
                request().
                post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE)).
                readEntity(String.class);

        RestTransferValue response = RestTransferValue.fromXML(resultXML);
        return response.getPatches();
    }
}
