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

import org.esa.pfa.classifier.Classifier;
import org.esa.pfa.classifier.ClassifierManager;
import org.esa.pfa.classifier.ClassifierModel;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
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


public class RestClassifierManager implements ClassifierManager {

    private final WebTarget target;
    private String applicationId;

    public RestClassifierManager(WebTarget target) {
        this.target = target;
    }

    @Override
    public synchronized String getApplicationId() {
        if (applicationId == null) {
            applicationId = target.path("applicationId").request().get().readEntity(String.class);
        }
        return applicationId;
    }

    @Override
    public String[] list() {
        return target.path("classifiers").request().get().readEntity(String.class).split("\n");
    }

    @Override
    public Classifier create(String classifierName) throws IOException {
        PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
        PFAApplicationDescriptor applicationDescriptor = applicationRegistry.getDescriptorById(getApplicationId());
        if (applicationDescriptor == null) {
            throw new IOException("Unknown application id " + getApplicationId());
        }
        ClassifierModel classifierModel = new ClassifierModel(applicationDescriptor.getName());

        Form form = new Form();

        WebTarget classifierTarget = target.path("classifier").path(classifierName);
        classifierTarget.request().post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));

        return new RestClassifier(classifierName, classifierModel, classifierTarget);
    }

    @Override
    public void delete(String classifierName) throws IOException {
        target.path("classifier").path(classifierName).request().delete();
    }

    @Override
    public Classifier get(String classifierName) throws IOException {
        Response response = target.path("classifier").path(classifierName).request().get();
        String classifierModelAsXML = response.readEntity(String.class);

        ClassifierModel model = ClassifierModel.fromXML(classifierModelAsXML);
        return new RestClassifier(classifierName, model, target.path("classifier").path(classifierName));
    }

    @Override
    public URI getPatchQuicklookUri(Patch patch, String quicklookBandName) throws IOException {
        return getPatchQuicklookTarget(patch, quicklookBandName).getUri();
    }

    @Override
    public BufferedImage getPatchQuicklook(Patch patch, String quicklookBandName) throws IOException {
        Response response = getPatchQuicklookTarget(patch, quicklookBandName).request().get();
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
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

    @Override
    public URI getFexOverviewUri(Patch patch) {
        return target.path("fex").path(patch.getParentProductName()).getUri();
    }
}
