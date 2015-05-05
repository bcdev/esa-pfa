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

import org.esa.pfa.classifier.ClassifierDelegate;
import org.esa.pfa.classifier.ClassifierManager;
import org.esa.pfa.classifier.ClassifierModel;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;


public class RestClassifierManagerClient implements ClassifierManager, RestClient {

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
    public String[] list() {
        WebTarget path = target.path("classifiers");
        Invocation.Builder request = path.request();
        Response response = request.get();
        String readEntity = response.readEntity(String.class);
        return readEntity.split("\n");
    }

    @Override
    public ClassifierDelegate create(String classifierName, String applicationName) throws IOException {
        ClassifierModel classifierModel = new ClassifierModel(applicationName);

        Form form = new Form();

        target.path("classifiers").path(classifierName).request().
                post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));

        PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
        PFAApplicationDescriptor applicationDescriptor = applicationRegistry.getDescriptorByName(applicationName);
        System.out.println("applicationDescriptor = " + applicationDescriptor);

        RestClassifier classifier = new RestClassifier(classifierName, classifierModel, this);
        return new ClassifierDelegate(classifierName, applicationDescriptor, classifier);
    }

    @Override
    public void delete(String classifierName) throws IOException {
        // TODO later
    }

    @Override
    public ClassifierDelegate get(String classifierName) throws IOException {
        WebTarget path = target.path("classifiers").path(classifierName);
        System.out.println("path = " + path);
        Response response = path.request().get();
        String classifierModelAsXML = response.readEntity(String.class);

        ClassifierModel classifierModel = ClassifierModel.fromXML(classifierModelAsXML);

        PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
        String applicationName = classifierModel.getApplicationName();
        PFAApplicationDescriptor applicationDescriptor = applicationRegistry.getDescriptorByName(applicationName);
        System.out.println("applicationDescriptor = " + applicationDescriptor);

        RestClassifier classifier = new RestClassifier(classifierName, classifierModel, this);
        return new ClassifierDelegate(classifierName, applicationDescriptor, classifier);
    }

    @Override
    public String populateArchivePatches(String classifierName, String modelXML) {
        Form form = new Form();
        form.param("modelXML", modelXML);

        Response response = target.path("classifiers").path(classifierName).path("populateArchivePatches").
                request().
                post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        return response.readEntity(String.class);
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        URI uri = new URI("http://localhost:8089/pfa/");
        RestClassifierManagerClient client = new RestClassifierManagerClient(uri, "AlgalBloom");
        String[] list = client.list();
        System.out.println("list = " + Arrays.toString(list));
        ClassifierDelegate classifier = client.get(list[0]);
        System.out.println("classifier = " + classifier);

        ClassifierDelegate classifierDelegate = client.create("web", "Algal Bloom Detection");
        System.out.println("classifierDelegate = " + classifierDelegate);
    }
}
