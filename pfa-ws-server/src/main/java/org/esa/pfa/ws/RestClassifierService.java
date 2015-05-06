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
import org.esa.pfa.classifier.PatchList;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.fe.op.Patch;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;


@Path("/v1/apps")
public class RestClassifierService {

//    private final File dbFile = new File("/home/marcoz/Scratch/pfa/output-snap");
    private final URI dbUri;

    public RestClassifierService() throws IOException, URISyntaxException {
        String dbUriProperty = System.getProperty("dbUri", null);
        dbUri = new File(dbUriProperty).toURI();
    }


    @GET
    @Path("/{appId}/classifiers")
    @Produces(MediaType.TEXT_PLAIN)
    public String listClassifiers(@PathParam(value = "appId") final String appId) throws IOException {
        System.out.println("WebClassifierManagerServer.list " + this);
        System.out.println("appId = " + appId);

        LocalClassifierManager localClassifierManager = new LocalClassifierManager(dbUri, appId);
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
            LocalClassifierManager localClassifierManager = new LocalClassifierManager(dbUri, appId);
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
            LocalClassifierManager localClassifierManager = new LocalClassifierManager(dbUri, appId);
            localClassifierManager.create(classifierName);
        } catch (Throwable ioe) {
            ioe.printStackTrace();
        }
    }

    @POST
    @Path("/{appId}/classifiers/{classifierName}/startTraining")
    public String populateArchivePatches(
            @PathParam(value = "appId") final String appId,
            @PathParam(value = "classifierName") final String classifierName,
            @FormParam("queryPatches") final String queryPatches
    ) {
        System.out.println("WebClassifierManagerServer.populateArchivePatches" + this);
        System.out.println("appId = [" + appId + "], classifierName = [" + classifierName + "]");
        System.out.println("queryPatches = " + queryPatches);

        try {
            LocalClassifierManager localClassifierManager = new LocalClassifierManager(dbUri, appId);
            java.nio.file.Path classifierPath = localClassifierManager.getClassifierPath(classifierName);
            if (!Files.exists(classifierPath)) {
                throw new IllegalArgumentException("Classifier does not exist. " + classifierName);
            }
            ClassifierModel classifierModel = ClassifierModel.fromFile(classifierPath.toFile());
            PFAApplicationDescriptor applicationDescriptor = PFAApplicationRegistry.getInstance().getDescriptorByName(classifierModel.getApplicationName());

            LocalClassifier localClassifier = new LocalClassifier(classifierModel, classifierPath, applicationDescriptor,
                                                                  localClassifierManager.getPatchPath(),
                                                                  localClassifierManager.getDbPath());
            Patch[] qPatches = PatchList.fromXML(queryPatches);
            Patch[] rPatches = localClassifier.startTraining(qPatches, ProgressMonitor.NULL);
            String resultXML = PatchList.toXML(rPatches);
            localClassifier.saveClassifier();

            return resultXML;
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    @POST
    @Path("/{appId}/classifiers/{classifierName}/trainAndClassify")
    public String trainAndClassify(
            @PathParam(value = "appId") final String appId,
            @PathParam(value = "classifierName") final String classifierName,
            @FormParam("labeledPatches") final String labeledPatches,
            @FormParam("prePopulate") final String prePopulateString
    ) {
        System.out.println("WebClassifierManagerServer.populateArchivePatches" + this);
        System.out.println("appId = [" + appId + "], classifierName = [" + classifierName + "]");
        System.out.println("labeledPatches = " + labeledPatches);
        System.out.println("prePopulate = " + prePopulateString);

        try {
            LocalClassifierManager localClassifierManager = new LocalClassifierManager(dbUri, appId);
            java.nio.file.Path classifierPath = localClassifierManager.getClassifierPath(classifierName);
            if (!Files.exists(classifierPath)) {
                throw new IllegalArgumentException("Classifier does not exist. " + classifierName);
            }
            ClassifierModel classifierModel = ClassifierModel.fromFile(classifierPath.toFile());
            PFAApplicationDescriptor applicationDescriptor = PFAApplicationRegistry.getInstance().getDescriptorByName(classifierModel.getApplicationName());

            LocalClassifier localClassifier = new LocalClassifier(classifierModel, classifierPath, applicationDescriptor,
                                                                  localClassifierManager.getPatchPath(),
                                                                  localClassifierManager.getDbPath());
            Patch[] lPatches = PatchList.fromXML(labeledPatches);
            boolean prePopulate = Boolean.parseBoolean(prePopulateString);
            Patch[] rPatches = localClassifier.trainAndClassify(prePopulate, lPatches, ProgressMonitor.NULL);
            String resultXML = PatchList.toXML(rPatches);
            localClassifier.saveClassifier();

            return resultXML;
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }


    @POST
    @Path("/{appId}/classifiers/{classifierName}/getMostAmbigous")
    public String getMostAmbigous(
            @PathParam(value = "appId") final String appId,
            @PathParam(value = "classifierName") final String classifierName,
            @FormParam("prePopulate") final String prePopulateString
    ) {
        System.out.println("WebClassifierManagerServer.populateArchivePatches" + this);
        System.out.println("appId = [" + appId + "], classifierName = [" + classifierName + "]");
        System.out.println("prePopulate = " + prePopulateString);

        try {
            LocalClassifierManager localClassifierManager = new LocalClassifierManager(dbUri, appId);
            java.nio.file.Path classifierPath = localClassifierManager.getClassifierPath(classifierName);
            if (!Files.exists(classifierPath)) {
                throw new IllegalArgumentException("Classifier does not exist. " + classifierName);
            }
            ClassifierModel classifierModel = ClassifierModel.fromFile(classifierPath.toFile());
            PFAApplicationDescriptor applicationDescriptor = PFAApplicationRegistry.getInstance().getDescriptorByName(classifierModel.getApplicationName());

            LocalClassifier localClassifier = new LocalClassifier(classifierModel, classifierPath, applicationDescriptor,
                                                                  localClassifierManager.getPatchPath(),
                                                                  localClassifierManager.getDbPath());
            boolean prePopulate = Boolean.parseBoolean(prePopulateString);
            Patch[] rPatches = localClassifier.getMostAmbigous(prePopulate, ProgressMonitor.NULL);
            String resultXML = PatchList.toXML(rPatches);
            localClassifier.saveClassifier();

            return resultXML;
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }


    // TODO
//    @DELETE
//    public void delete() {
//
//    }

}
