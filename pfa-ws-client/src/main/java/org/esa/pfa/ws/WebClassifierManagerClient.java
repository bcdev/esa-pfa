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

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;


public class WebClassifierManagerClient implements ClassifierManager {

    private final WebTarget target;
    private final URI uri;

    public WebClassifierManagerClient(URI uri) {
        this.uri = uri;
        Client client = ClientBuilder.newClient();
        this.target = client.target(uri).path("manager");
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public String[] list() {
        Response response = target.path("list").request().get();
        String readEntity = response.readEntity(String.class);
        return readEntity.split("\n");
    }

    @Override
    public ClassifierDelegate create(String classifierName, String applicationName) throws IOException {
//        PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
//        PFAApplicationDescriptor applicationDescriptor = applicationRegistry.getDescriptor(applicationName);
//        RealWebClassifier realClassifier = new RealWebClassifier(classifierName, applicationName);
//        realClassifier.saveClassifier();
//        return new Classifier(classifierName, applicationDescriptor, realClassifier);
        return null;
    }

    @Override
    public void delete(String classifierName) throws IOException {

    }

    @Override
    public ClassifierDelegate get(String classifierName) throws IOException {
        Response response = target.path("getClassifier").queryParam("classifierName", classifierName).request().get();
        String readEntity = response.readEntity(String.class);
        System.out.println("get: readEntity = " + readEntity);

//        PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
//        PFAApplicationDescriptor applicationDescriptor = applicationRegistry.getDescriptor(applicationName);
//        RealWebClassifier realClassifier = new RealWebClassifier(classifierName, applicationName);
//        realClassifier.saveClassifier();
//        return new Classifier(classifierName, applicationDescriptor, realClassifier);
        return null;
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        URI uri = new URI("http://localhost:8089/pfa/");
        WebClassifierManagerClient client = new WebClassifierManagerClient(uri);
        String[] list = client.list();
        System.out.println("list = " + Arrays.toString(list));
        ClassifierDelegate classifier = client.get(list[0]);
        System.out.println("classifier = " + classifier);
    }
}
