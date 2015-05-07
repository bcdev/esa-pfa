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
import org.esa.pfa.classifier.LocalClassifier;
import org.esa.pfa.classifier.LocalClassifierManager;
import org.esa.pfa.fe.PatchAccess;
import org.esa.pfa.fe.op.Patch;

import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;


@Path("/v1/apps")
public class RestClassifierService {

    private final URI dbUri;

    public RestClassifierService() throws IOException, URISyntaxException {
        String dbUriProperty = System.getProperty("dbUri", null);
        dbUri = new File(dbUriProperty).toURI();
    }


    @GET
    @Path("/{appId}/classifiers")
    @Produces(MediaType.TEXT_PLAIN)
    public String listClassifiers(@PathParam(value = "appId") String appId) throws IOException {
        System.out.println("list appId = [" + appId + "]");

        LocalClassifierManager localClassifierManager = new LocalClassifierManager(dbUri, appId);
        return String.join("\n", localClassifierManager.list());
    }

    @GET
    @Path("/{appId}/classifiers/{classifierName}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getClassifier(
            @PathParam(value = "appId") String appId,
            @PathParam(value = "classifierName") String classifierName
    ) {
        System.out.println("getClassifier appId = [" + appId + "], classifierName = [" + classifierName + "]");

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
            @PathParam(value = "appId") String appId,
            @PathParam(value = "classifierName") String classifierName
    ) {
        System.out.println("createClassifier appId = [" + appId + "], classifierName = [" + classifierName + "]");

        try {
            LocalClassifierManager localClassifierManager = new LocalClassifierManager(dbUri, appId);
            localClassifierManager.create(classifierName);
        } catch (Throwable ioe) {
            ioe.printStackTrace();
        }
    }

    @POST
    @Path("/{appId}/classifiers/{classifierName}/setNumTrainingImages")
    public void setNumTrainingImages(
            @PathParam(value = "appId") String appId,
            @PathParam(value = "classifierName") String classifierName,
            @QueryParam(value = "value") int numTrainingImages
    ) {
        System.out.println("setNumTrainingImages appId = [" + appId + "], classifierName = [" + classifierName + "], numTrainingImages = [" + numTrainingImages + "]");

        try {
            LocalClassifier localClassifier = getLocalClassifier(appId, classifierName);
            localClassifier.setNumTrainingImages(numTrainingImages);
            localClassifier.saveClassifier();
        } catch (Throwable ioe) {
            ioe.printStackTrace();
        }
    }

    @POST
    @Path("/{appId}/classifiers/{classifierName}/setNumRetrievedImages")
    public void setNumRetrievedImages(
            @PathParam(value = "appId") String appId,
            @PathParam(value = "classifierName") String classifierName,
            @QueryParam(value = "value") int numRetrievedImages
    ) {
        System.out.println("createClassifier appId = [" + appId + "], classifierName = [" + classifierName + "], numRetrievedImages = [" + numRetrievedImages + "]");

        try {
            LocalClassifier localClassifier = getLocalClassifier(appId, classifierName);
            localClassifier.setNumRetrievedImages(numRetrievedImages);
            localClassifier.saveClassifier();
        } catch (Throwable ioe) {
            ioe.printStackTrace();
        }
    }


    @POST
    @Path("/{appId}/classifiers/{classifierName}/startTraining")
    public String populateArchivePatches(
            @PathParam(value = "appId") String appId,
            @PathParam(value = "classifierName") String classifierName,
            @FormParam("queryPatches") String queryPatches
    ) {
        System.out.println("startTraining appId = [" + appId + "], classifierName = [" + classifierName + "]");

        try {
            LocalClassifier localClassifier = getLocalClassifier(appId, classifierName);

            RestTransferValue query = RestTransferValue.fromXML(queryPatches);
            Patch[] rPatches = localClassifier.startTraining(query.getPatches(), ProgressMonitor.NULL);
            localClassifier.saveClassifier();

            RestTransferValue response = new RestTransferValue();
            response.setPatches(rPatches);
            response.setNumIterations(localClassifier.getNumIterations());
            return response.toXML();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    @POST
    @Path("/{appId}/classifiers/{classifierName}/trainAndClassify")
    public String trainAndClassify(
            @PathParam(value = "appId") String appId,
            @PathParam(value = "classifierName") String classifierName,
            @FormParam("labeledPatches") String labeledPatches,
            @FormParam("prePopulate") String prePopulateString
    ) {
        System.out.println("trainAndClassify appId = [" + appId + "], classifierName = [" + classifierName + "]");

        try {
            LocalClassifier localClassifier = getLocalClassifier(appId, classifierName);

            RestTransferValue query = RestTransferValue.fromXML(labeledPatches);
            boolean prePopulate = Boolean.parseBoolean(prePopulateString);
            Patch[] rPatches = localClassifier.trainAndClassify(prePopulate, query.getPatches(), ProgressMonitor.NULL);
            localClassifier.saveClassifier();

            RestTransferValue response = new RestTransferValue();
            response.setPatches(rPatches);
            response.setNumIterations(localClassifier.getNumIterations());
            return response.toXML();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }


    @POST
    @Path("/{appId}/classifiers/{classifierName}/getMostAmbigous")
    public String getMostAmbigous(
            @PathParam(value = "appId") String appId,
            @PathParam(value = "classifierName") String classifierName,
            @FormParam("prePopulate") String prePopulateString
    ) {
        System.out.println("getMostAmbigous appId = [" + appId + "], classifierName = [" + classifierName + "]");

        try {
            LocalClassifier localClassifier = getLocalClassifier(appId, classifierName);

            boolean prePopulate = Boolean.parseBoolean(prePopulateString);
            Patch[] rPatches = localClassifier.getMostAmbigous(prePopulate, ProgressMonitor.NULL);
            localClassifier.saveClassifier();

            RestTransferValue response = new RestTransferValue();
            response.setPatches(rPatches);
            response.setNumIterations(localClassifier.getNumIterations());
            return response.toXML();
        } catch (Throwable e) {
            e.printStackTrace();
            return "";
        }
    }


    @GET
    @Path("/{appId}/quicklook")
    @Produces("image/png")
    public Response getPatchQuicklook(
            @PathParam(value = "appId") String appId,
            @QueryParam("parentProductName") String parentProductName,
            @QueryParam("patchX") int patchX,
            @QueryParam("patchY") int patchY,
            @QueryParam ("quicklookBandName") String quicklookBandName
    ) {
        System.out.println("quicklook appId = [" + appId + "],parentProductName = [" + parentProductName + "], patchX = [" + patchX + "], patchY = [" + patchY + "], quicklookBandName = [" + quicklookBandName + "]");
        try {
           PatchAccess patchAccess = new PatchAccess(Paths.get(dbUri).toFile(), null);
            java.nio.file.Path patchImagePath = patchAccess.getPatchImagePath(parentProductName, patchX, patchY, quicklookBandName);
            if (Files.exists(patchImagePath)) {
                InputStream inputStream = Files.newInputStream(patchImagePath);
                return Response.ok().header("name", quicklookBandName).entity(inputStream).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @DELETE
    @Path("/{appId}/classifiers/{classifierName}")
    public void delete(
        @PathParam(value = "appId") String appId,
        @PathParam(value = "classifierName") String classifierName
    ) {
        System.out.println("deleteClassifier appId = [" + appId + "], classifierName = [" + classifierName + "]");
        try {
            LocalClassifierManager localClassifierManager = new LocalClassifierManager(dbUri, appId);
            localClassifierManager.delete(classifierName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private LocalClassifier getLocalClassifier(String appId, String classifierName) throws IOException {
        LocalClassifierManager localClassifierManager = new LocalClassifierManager(dbUri, appId);
        java.nio.file.Path classifierPath = localClassifierManager.getClassifierPath(classifierName);
        if (!Files.exists(classifierPath)) {
            throw new IllegalArgumentException("Classifier does not exist. " + classifierName);
        }
        return LocalClassifier.loadClassifier(classifierName, classifierPath,
                                                  localClassifierManager.getPatchPath(),
                                                  localClassifierManager.getDbPath());
    }

}
