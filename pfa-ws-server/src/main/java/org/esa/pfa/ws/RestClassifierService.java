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
import org.esa.pfa.classifier.ClassifierModel;
import org.esa.pfa.classifier.LocalClassifier;
import org.esa.pfa.classifier.LocalClassifierManager;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;


@Path("/v1/apps")
public class RestClassifierService {

    private final LocalClassifierManager localClassifierManager;

    public RestClassifierService() throws IOException, URISyntaxException {
        System.out.println("WebClassifierManagerServer.WebClassifierManagerServer " + this);

        File file = new File("/home/marcoz/Scratch/pfa/output-snap");
        localClassifierManager = new LocalClassifierManager(file.toURI());
    }


    @GET
    @Path("/{appId}/classifiers")
    @Produces(MediaType.TEXT_PLAIN)
    public String listClassifiers(@PathParam(value = "appId") final String appId) throws IOException {
        System.out.println("WebClassifierManagerServer.list " + this);
        System.out.println("appId = " + appId);

        return String.join("\n", localClassifierManager.list());
    }

    @GET
    @Path("/{appId}/classifiers/{classifierName}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getClassifier(
            @PathParam(value = "appId") final String appId,
            @PathParam(value = "classifierName") final String classifierName
    ) {
        System.out.println("WebClassifierManagerServer.getClassifier" + this);
        System.out.println("appId = [" + appId + "], classifierName = [" + classifierName + "]");

        try {
            java.nio.file.Path classifierPath = localClassifierManager.getClassifierPath(classifierName);
            if (!Files.exists(classifierPath)) {
                throw new IllegalArgumentException("Classifier does not exist. " + classifierName);
            }
            return new String(Files.readAllBytes(classifierPath));
        } catch (Throwable ioe) {
            ioe.printStackTrace();
            return "";
        }
    }

    @POST
    @Path("/{appId}/classifiers/{classifierName}")
    public void createClassifier(
            @PathParam(value = "appId") final String appId,
            @PathParam(value = "classifierName") final String classifierName
    ) {
        System.out.println("WebClassifierManagerServer.createClassifier" + this);
        System.out.println("appId = [" + appId + "], classifierName = [" + classifierName + "]");

        try {
            String appName = PFAApplicationRegistry.getInstance().getDescriptorById(appId).getName();
            localClassifierManager.create(classifierName, appName);
        } catch (Throwable ioe) {
            ioe.printStackTrace();
        }
    }

    @POST
    @Path("/{appId}/classifiers/{classifierName}/populateArchivePatches")
    public String populateArchivePatches(
            @PathParam(value = "appId") final String appId,
            @PathParam(value = "classifierName") final String classifierName,
            @FormParam("modelXML") final String modelXML
    ) {
        System.out.println("WebClassifierManagerServer.populateArchivePatches" + this);
        System.out.println("appId = [" + appId + "], classifierName = [" + classifierName + "]");
        System.out.println("modelXML = " + modelXML);

        try {
            java.nio.file.Path classifierPath = localClassifierManager.getClassifierPath(classifierName);
            if (!Files.exists(classifierPath)) {
                throw new IllegalArgumentException("Classifier does not exist. " + classifierName);
            }
            ClassifierModel classifierModel = ClassifierModel.fromFile(classifierPath.toFile());
            PFAApplicationDescriptor applicationDescriptor = PFAApplicationRegistry.getInstance().getDescriptorByName(classifierModel.getApplicationName());

            LocalClassifier localClassifier = new LocalClassifier(classifierModel, classifierPath, applicationDescriptor,
                                                                  localClassifierManager.getPatchPath(),
                                                                  localClassifierManager.getDbPath());
            localClassifier.getActiveLearning().setTrainingData(ProgressMonitor.NULL);
            localClassifier.populateArchivePatches(ProgressMonitor.NULL);
            localClassifier.saveClassifier();

            return  classifierModel.toXML();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }



//    @DELETE
//    public void delete() {
//
//    }

}
