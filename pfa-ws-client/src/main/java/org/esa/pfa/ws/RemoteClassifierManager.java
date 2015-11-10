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


class RemoteClassifierManager implements ClassifierManager {

    private final String databaseName;
    private final WebTarget target;
    private String applicationId;

    RemoteClassifierManager(String databaseName, WebTarget target) {
        this.databaseName = databaseName;
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
    public String getDatabaseName() {
        return databaseName;
    }

    @Override
    public String[] list() {
        return target.path("classifiers").request().get().readEntity(String.class).split("\n");
    }

    @Override
    public Classifier create(String classifierName) throws IOException {
        WebTarget classifierTarget = target.path("classifier").path(classifierName);
        classifierTarget.request().post(Entity.entity(new Form(), MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        return new RemoteClassifier(classifierName, classifierTarget);
    }

    @Override
    public void delete(String classifierName) throws IOException {
        target.path("classifier").path(classifierName).request().delete();
    }

    @Override
    public Classifier get(String classifierName) throws IOException {
        return new RemoteClassifier(classifierName, target.path("classifier").path(classifierName));
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
