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
import org.esa.pfa.classifier.PatchList;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.fe.op.Patch;

import javax.imageio.ImageIO;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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
      public String getApplicationId() {
          return appId;
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
    public Classifier create(String classifierName) throws IOException {
        PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
        PFAApplicationDescriptor applicationDescriptor = applicationRegistry.getDescriptorById(appId);
        ClassifierModel classifierModel = new ClassifierModel(applicationDescriptor.getName());

        Form form = new Form();

        target.path("classifiers").path(classifierName).request().
                post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));


        System.out.println("applicationDescriptor = " + applicationDescriptor);

        return new RestClassifier(classifierName, classifierModel, this);
    }

    @Override
    public void delete(String classifierName) throws IOException {
        // TODO later
    }

    @Override
    public Classifier get(String classifierName) throws IOException {
        WebTarget path = target.path("classifiers").path(classifierName);
        System.out.println("path = " + path);
        Response response = path.request().get();
        String classifierModelAsXML = response.readEntity(String.class);

        ClassifierModel classifierModel = ClassifierModel.fromXML(classifierModelAsXML);

        PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
        String applicationName = classifierModel.getApplicationName();
        PFAApplicationDescriptor applicationDescriptor = applicationRegistry.getDescriptorByName(applicationName);
        System.out.println("applicationDescriptor = " + applicationDescriptor);

        return new RestClassifier(classifierName, classifierModel, this);
    }

    @Override
    public Patch[] startTraining(String classifierName, Patch[] queryPatches) throws IOException {
        String xml = PatchList.toXML(queryPatches);
        Form form = new Form();
        form.param("queryPatches", xml);

        Response response = target.path("classifiers").path(classifierName).path("startTraining").
                request().
                post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        String resultPatchesXML = response.readEntity(String.class);
        return PatchList.fromXML(resultPatchesXML);
    }

    @Override
    public Patch[] trainAndClassify(String classifierName, boolean prePopulate, Patch[] labeledPatches) throws IOException {
        String xml = PatchList.toXML(labeledPatches);
        Form form = new Form();
        form.param("labeledPatches", xml);
        form.param("prePopulate", Boolean.toString(prePopulate));

        Response response = target.path("classifiers").path(classifierName).path("trainAndClassify").
                request().
                post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        String resultPatchesXML = response.readEntity(String.class);
        return PatchList.fromXML(resultPatchesXML);
    }

    @Override
    public Patch[] getMostAmbigous(String classifierName, boolean prePopulate) throws IOException {
        Form form = new Form();
        form.param("prePopulate", Boolean.toString(prePopulate));

        Response response = target.path("classifiers").path(classifierName).path("getMostAmbigous").
                request().
                post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        String resultPatchesXML = response.readEntity(String.class);
        return PatchList.fromXML(resultPatchesXML);
    }

    @Override
    public URI getPatchQuicklookUri(String classifierName, Patch patch, String quicklookBandName) throws IOException {
        WebTarget webTarget = getPatchQuicklookTarget(patch, quicklookBandName);
        return webTarget.getUri();
    }

    @Override
    public BufferedImage getPatchQuicklook(String classifierName, Patch patch, String quicklookBandName) throws IOException {
        WebTarget webTarget = getPatchQuicklookTarget(patch, quicklookBandName);
        Response response = webTarget.request().get();
        return response.readEntity(BufferedImage.class);
    }

    private WebTarget getPatchQuicklookTarget(Patch patch, String quicklookBandName) {
        String parentProductName = patch.getParentProductName();
        int patchX = patch.getPatchX();
        int patchY = patch.getPatchY();
        return target.path("quicklook")
                .queryParam("parentProductName", parentProductName)
                .queryParam("patchX", patchX)
                .queryParam("patchY", patchY)
                .queryParam("quicklookBandName", quicklookBandName);
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        URI uri = new URI("http://localhost:8089/pfa/");
        RestClassifierManagerClient client = new RestClassifierManagerClient(uri, "AlgalBloom");
        String[] list = client.list();
        System.out.println("list = " + Arrays.toString(list));
        Classifier classifier = client.get(list[0]);
        System.out.println("classifier = " + classifier);

        Classifier classifierDelegate = client.create("web");
        System.out.println("classifierDelegate = " + classifierDelegate);
    }
}
