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

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Arrays;


public class WebClassifierManagerClient implements ClassifierManager {

    private final WebTarget target;
    private final String responsibleURL;

    public WebClassifierManagerClient(String responsibleURL) {
        this.responsibleURL = responsibleURL;
        Client client = ClientBuilder.newClient();
        this.target = client.target("http://localhost:8089/pfa/").path("manager");
    }

    @Override
    public String getResponsibleURL() {
        return responsibleURL;
    }

    @Override
    public String[] list() {
        Response response = target.path("list").request().get();
        String readEntity = response.readEntity(String.class);
        return readEntity.split(",");
    }

    @Override
    public Classifier create(String classifierName, String applicationName) throws IOException {
        return null;
    }

    @Override
    public void delete(String classifierName) throws IOException {

    }

    @Override
    public Classifier get(String classifierName) throws IOException {
        return null;
    }

    public static void main(String[] args) throws IOException {
        WebClassifierManagerClient client = new WebClassifierManagerClient("foo");
        String[] list = client.list();
        System.out.println("list = " + Arrays.toString(list));
        Classifier classifier = client.get(list[0]);
        System.out.println("classifier = " + classifier);
    }
}
