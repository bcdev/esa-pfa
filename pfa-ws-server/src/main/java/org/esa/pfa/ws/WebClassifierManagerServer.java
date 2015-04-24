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

import org.esa.pfa.classifier.ClassifierManager;
import org.esa.pfa.classifier.LocalClassifierManager;
import org.esa.pfa.classifier.RealLocalClassifier;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;


@Path("manager")
public class WebClassifierManagerServer {

    private final ClassifierManager localClassifier;

    public WebClassifierManagerServer() throws IOException {
        System.out.println("WebClassifierManagerServer.WebClassifierManagerServer");
        localClassifier = new LocalClassifierManager("/home/marcoz/Scratch/pfa/output");
    }

    @GET
    @Path("list")
    @Produces(MediaType.TEXT_PLAIN)
    public String list() throws IOException {
        return String.join(",", localClassifier.list());
    }
}
