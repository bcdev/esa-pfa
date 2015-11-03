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
import org.esa.pfa.classifier.DatabaseManager;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;

public class RestDatabaseManager implements DatabaseManager {

    private final URI uri;
    private final WebTarget target;

    public RestDatabaseManager(URI uri) {
        this.uri = uri;
        ClientConfig configuration = new ClientConfig();
        configuration = configuration.property(ClientProperties.CONNECT_TIMEOUT, 1000); // in ms
        configuration = configuration.property(ClientProperties.READ_TIMEOUT, 1000);    // in ms
        Client client = ClientBuilder.newClient(configuration);

        this.target = client.target(uri).path("v1");
    }

    public boolean isAlive() {
        try {
            Response response = target.path("alive").request().get();
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                if ("true".equals(response.readEntity(String.class))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public String[] listDatabases() {
        Response response = target.path("dbs").request().get();
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            return response.readEntity(String.class).split("\n");
        }
        throw new IllegalArgumentException("Failed to retrieve list of databases from server:" + response);
    }

    @Override
    public ClassifierManager createClassifierManager(String databaseName) throws IOException {
        return new RestClassifierManager(databaseName, target.path("db").path(databaseName));
    }
}
