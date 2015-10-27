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
import org.esa.pfa.classifier.ClassifierManager;
import org.esa.pfa.classifier.ClassifierStats;
import org.esa.pfa.classifier.LocalClassifier;
import org.esa.pfa.classifier.LocalClassifierManager;
import org.esa.pfa.classifier.LocalDatabaseManager;
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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;


@Path("/v1")
public class RestClassifierService {

    private final LocalDatabaseManager localDbManager;

    public RestClassifierService() throws IOException, URISyntaxException {
        localDbManager = CachingLocalDatabaseManager.getInstance();
    }

    ////////////////////////////////////////////////////////////////////////////////////

    @GET
    @Path("/dbs")
    @Produces(MediaType.TEXT_PLAIN)
    public String listDatabases() throws IOException {

        //System.out.println("listDatabases");

        String[] databases = localDbManager.listDatabases();

        //System.out.println("listDatabases = " + String.join("\n", databases));

        return String.join("\n", databases);
    }

    ////////////////////////////////////////////////////////////////////////////////////

    @GET
    @Path("/db/{databaseName}/applicationId")
    @Produces(MediaType.TEXT_PLAIN)
    public String getApplicationId(@PathParam(value = "databaseName") String databaseName) throws IOException {

        //System.out.println("getApplicationId databaseName = [" + databaseName + "]");
        try {
            ClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            return classifierManager.getApplicationId();
        } catch (Throwable ioe) {
            ioe.printStackTrace();
            return "";
        }
    }

    @GET
    @Path("/db/{databaseName}/classifiers")
    @Produces(MediaType.TEXT_PLAIN)
    public String listClassifiers(@PathParam(value = "databaseName") String databaseName) throws IOException {

        //System.out.println("listClassifiers databaseName = [" + databaseName + "]");

        ClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);

        //System.out.println("listClassifiers databaseName = [" + databaseName + "] = " + String.join("\n", classifierManager.list()));

        return String.join("\n", classifierManager.list());
    }

    @GET
    @Path("/db/{databaseName}/classifier/{classifierName}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getClassifier(@PathParam(value = "databaseName") String databaseName,
                                @PathParam(value = "classifierName") String classifierName) {

        //System.out.println("getClassifier databaseName = [" + databaseName + "], classifierName = [" + classifierName + "]");

        try {
            LocalClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            java.nio.file.Path classifierPath = classifierManager.getClassifierPath(classifierName);
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
    @Path("/db/{databaseName}/classifier/{classifierName}")
    public void createClassifier(
            @PathParam(value = "databaseName") String databaseName,
            @PathParam(value = "classifierName") String classifierName) {

        //System.out.println("createClassifier databaseName = [" + databaseName + "], classifierName = [" + classifierName + "]");

        try {
            LocalClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            classifierManager.create(classifierName);
        } catch (Throwable ioe) {
            ioe.printStackTrace();
        }
    }

    @DELETE
    @Path("/db/{databaseName}/classifier/{classifierName}")
    public void deleteClassifier(
            @PathParam(value = "databaseName") String databaseName,
            @PathParam(value = "classifierName") String classifierName) {

        //System.out.println("deleteClassifier databaseName = [" + databaseName + "], classifierName = [" + classifierName + "]");

        try {
            LocalClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            classifierManager.delete(classifierName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @POST
    @Path("/db/{databaseName}/classifier/{classifierName}/setNumTrainingImages")
    public void setNumTrainingImages(
            @PathParam(value = "databaseName") String databaseName,
            @PathParam(value = "classifierName") String classifierName,
            @QueryParam(value = "value") int numTrainingImages) {
        try {
            LocalClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            LocalClassifier classifier = classifierManager.get(classifierName);
            classifier.setNumTrainingImages(numTrainingImages);
            classifier.saveClassifier();
        } catch (Throwable ioe) {
            ioe.printStackTrace();
        }
    }

    @POST
    @Path("/db/{databaseName}/classifier/{classifierName}/setNumRetrievedImages")
    public void setNumRetrievedImages(
            @PathParam(value = "databaseName") String databaseName,
            @PathParam(value = "classifierName") String classifierName,
            @QueryParam(value = "value") int numRetrievedImages) {
        try {
            LocalClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            LocalClassifier classifier = classifierManager.get(classifierName);
            classifier.setNumRetrievedImages(numRetrievedImages);
            classifier.saveClassifier();
        } catch (Throwable ioe) {
            ioe.printStackTrace();
        }
    }

    @POST
    @Path("/db/{databaseName}/classifier/{classifierName}/setNumRetrievedImagesMax")
    public void setNumRetrievedImagesMax(
            @PathParam(value = "databaseName") String databaseName,
            @PathParam(value = "classifierName") String classifierName,
            @QueryParam(value = "value") int numRetrievedImagesMax) {
        try {
            LocalClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            LocalClassifier classifier = classifierManager.get(classifierName);
            classifier.setNumRetrievedImagesMax(numRetrievedImagesMax);
            classifier.saveClassifier();
        } catch (Throwable ioe) {
            ioe.printStackTrace();
        }
    }

    @POST
    @Path("/db/{databaseName}/classifier/{classifierName}/setNumRandomImages")
    public void setNumRandomImages(
            @PathParam(value = "databaseName") String databaseName,
            @PathParam(value = "classifierName") String classifierName,
            @QueryParam(value = "value") int numRandomImages) {
        try {
            LocalClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            LocalClassifier classifier = classifierManager.get(classifierName);
            classifier.setNumRandomImages(numRandomImages);
            classifier.saveClassifier();
        } catch (Throwable ioe) {
            ioe.printStackTrace();
        }
    }


    @GET
    @Path("/db/{databaseName}/classifier/{classifierName}/getClassifierStats")
    @Produces(MediaType.TEXT_PLAIN)

    public String getClassifierStats(
            @PathParam(value = "databaseName") String databaseName,
            @PathParam(value = "classifierName") String classifierName) {

        try {
            LocalClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            LocalClassifier classifier = classifierManager.get(classifierName);
            ClassifierStats classifierStats = classifier.getClassifierStats();
            int numTrainingImages = classifierStats.getNumTrainingImages();
            int numRetrievedImages = classifierStats.getNumRetrievedImages();
            int numRetrievedImagesMax = classifierStats.getNumRetrievedImagesMax();
            int numRandomImages = classifierStats.getNumRandomImages();
            int numIterations = classifierStats.getNumIterations();
            int numPatchesInTestData = classifierStats.getNumPatchesInTestData();
            int numPatchesInQueryData = classifierStats.getNumPatchesInQueryData();
            int numPatchesInTrainingData = classifierStats.getNumPatchesInTrainingData();
            int numPatchesInDatabase = classifierStats.getNumPatchesInDatabase();

            StringBuilder sb = new StringBuilder();
            sb.append(numTrainingImages).append(" ");
            sb.append(numRetrievedImages).append(" ");
            sb.append(numRetrievedImagesMax).append(" ");
            sb.append(numRandomImages).append(" ");
            sb.append(numIterations).append(" ");
            sb.append(numPatchesInTestData).append(" ");
            sb.append(numPatchesInQueryData).append(" ");
            sb.append(numPatchesInTrainingData).append(" ");
            sb.append(numPatchesInDatabase);

            String s = sb.toString();
            System.out.println("getClassifierStats = " + s);
            return s;
        } catch (Throwable ioe) {
            ioe.printStackTrace();
            return "";
        }
    }

    @POST
    @Path("/db/{databaseName}/classifier/{classifierName}/startTraining")
    public String populateArchivePatches(
            @PathParam(value = "databaseName") String databaseName,
            @PathParam(value = "classifierName") String classifierName,
            @FormParam("queryPatches") String queryPatches) {

        //System.out.println("populateArchivePatches databaseName = [" + databaseName + "], classifierName = [" + classifierName + "]");

        try {
            LocalClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            LocalClassifier classifier = classifierManager.get(classifierName);

            RestTransferValue query = RestTransferValue.fromXML(queryPatches);
            Patch[] rPatches = classifier.startTraining(query.getPatches(), ProgressMonitor.NULL);
            classifier.saveClassifier();

            RestTransferValue response = new RestTransferValue();
            response.setPatches(rPatches);
            return response.toXML();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    @POST
    @Path("/db/{databaseName}/classifier/{classifierName}/trainAndClassify")
    public String trainAndClassify(
            @PathParam(value = "databaseName") String databaseName,
            @PathParam(value = "classifierName") String classifierName,
            @FormParam("labeledPatches") String labeledPatches,
            @FormParam("prePopulate") String prePopulateString) {

        System.out.println("trainAndClassify databaseName = [" + databaseName + "], classifierName = [" + classifierName + "]");

        try {
            LocalClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            LocalClassifier classifier = classifierManager.get(classifierName);

            RestTransferValue query = RestTransferValue.fromXML(labeledPatches);
            boolean prePopulate = Boolean.parseBoolean(prePopulateString);
            Patch[] rPatches = classifier.trainAndClassify(prePopulate, query.getPatches(), ProgressMonitor.NULL);
            classifier.saveClassifier();

            RestTransferValue response = new RestTransferValue();
            response.setPatches(rPatches);
            return response.toXML();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    @POST
    @Path("/db/{databaseName}/classifier/{classifierName}/getMostAmbigous")
    public String getMostAmbigous(
            @PathParam(value = "databaseName") String databaseName,
            @PathParam(value = "classifierName") String classifierName,
            @FormParam("prePopulate") String prePopulateString) {

        System.out.println("getMostAmbigous databaseName = [" + databaseName + "], classifierName = [" + classifierName + "]");

        try {
            LocalClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            LocalClassifier classifier = classifierManager.get(classifierName);

            boolean prePopulate = Boolean.parseBoolean(prePopulateString);
            Patch[] rPatches = classifier.getMostAmbigous(prePopulate, ProgressMonitor.NULL);
            classifier.saveClassifier();

            RestTransferValue response = new RestTransferValue();
            response.setPatches(rPatches);
            return response.toXML();
        } catch (Throwable e) {
            e.printStackTrace();
            return "";
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////

    @GET
    @Produces("image/png")
    @Path("/db/{databaseName}/quicklook/{parentProductName}/{patchX}/{patchY}/{quicklookBandName}")
    public Response getPatchQuicklook(
            @PathParam(value = "databaseName") String databaseName,
            @PathParam("parentProductName") String parentProductName,
            @PathParam("patchX") int patchX,
            @PathParam("patchY") int patchY,
            @PathParam("quicklookBandName") String quicklookBandName) {

        //System.out.println("quicklook databaseName = [" + databaseName + "], parentProductName = [" + parentProductName + "], patchX = [" + patchX + "], patchY = [" + patchY + "], quicklookBandName = [" + quicklookBandName + "]");

        try {
            LocalClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            PatchAccess patchAccess = classifierManager.getPatchAccess();

            java.nio.file.Path patchImagePath = patchAccess.getPatchImagePath(parentProductName, patchX, patchY, quicklookBandName);
            if (Files.exists(patchImagePath)) {
                InputStream inputStream = Files.newInputStream(patchImagePath);
                return Response.ok().header("name", quicklookBandName).entity(inputStream).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("/db/{databaseName}/features/{parentProductName}/{patchX}/{patchY}")
    public String getFeaturesAsText(
            @PathParam(value = "databaseName") String databaseName,
            @PathParam("parentProductName") String parentProductName,
            @PathParam("patchX") int patchX,
            @PathParam("patchY") int patchY) {

        //System.out.println("features databaseName = [" + databaseName + "], parentProductName = [" + parentProductName + "], patchX = [" + patchX + "], patchY = [" + patchY + "]");

        try {
            LocalClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            PatchAccess patchAccess = classifierManager.getPatchAccess();
            return patchAccess.getFeaturesAsText(parentProductName, patchX, patchY);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    @GET
    @Path("/db/{databaseName}/fex/{parentProductName}")
    public Response fex(
            @Context final UriInfo uriInfo,
            @PathParam(value = "databaseName") String databaseName,
            @PathParam("parentProductName") String parentProductName
    ) {
        //System.out.println("fex databaseName = [" + databaseName + "], parentProductName = [" + parentProductName + "]");

        final URI fexUri = uriInfo.getBaseUri().resolve("v1/db/" + databaseName + "/fex/" + parentProductName + "/fex-overview.html");
        return Response.temporaryRedirect(fexUri).build();
    }

    @GET
    @Path("/db/{databaseName}/fex/{parentProductName}/{localPart : .+}")
    public Response fexLocalPart(
            @PathParam(value = "databaseName") String databaseName,
            @PathParam("parentProductName") String parentProductName,
            @PathParam("localPart") String localPart
    ) {
        //System.out.println("fexLocalPart databaseName = [" + databaseName + "], parentProductName = [" + parentProductName + "], localPart = [" + localPart + "]");
        try {
            LocalClassifierManager classifierManager = localDbManager.createClassifierManager(databaseName);
            PatchAccess patchAccess = classifierManager.getPatchAccess();
            final java.nio.file.Path fexPath = patchAccess.findFexPath(parentProductName);
            if (fexPath != null) {
                final java.nio.file.Path fexResource = fexPath.resolve(localPart);
                InputStream inputStream = Files.newInputStream(fexResource);
                String mediaType = MediaType.TEXT_PLAIN;
                if (localPart.endsWith(".html")) {
                    mediaType = MediaType.TEXT_HTML;
                } else if (localPart.endsWith(".png")) {
                    mediaType = "image/png";
                }
                return Response.ok().entity(inputStream).type(mediaType).build();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }
}
