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

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;


public class RestClassifierManagerClient implements ClassifierManager {

    private final WebTarget target;
    private final URI uri;
    private final String appId;

    public RestClassifierManagerClient(URI uri, String appId) {
        this.uri = uri;
        this.appId = appId;
        Client client = ClientBuilder.newClient();
        this.target = client.target(uri).path("v1/apps").path(appId);
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
        return target.path("classifiers").request().get().readEntity(String.class).split("\n");
    }

    @Override
    public Classifier create(String classifierName) throws IOException {
        PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
        PFAApplicationDescriptor applicationDescriptor = applicationRegistry.getDescriptorById(appId);
        ClassifierModel classifierModel = new ClassifierModel(applicationDescriptor.getName());

        Form form = new Form();

        target.path("classifiers").path(classifierName).request().
                post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));

        return new RestClassifier(classifierName, classifierModel, target);
    }

    @Override
    public void delete(String classifierName) throws IOException {
        target.path("classifiers").path(classifierName).request().delete();
    }

    @Override
    public Classifier get(String classifierName) throws IOException {
        Response response = target.path("classifiers").path(classifierName).request().get();
        String classifierModelAsXML = response.readEntity(String.class);

        ClassifierModel model = ClassifierModel.fromXML(classifierModelAsXML);
        return new RestClassifier(classifierName,   model, target);
    }
}
