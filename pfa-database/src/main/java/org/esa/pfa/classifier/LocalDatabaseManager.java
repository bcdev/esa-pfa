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

package org.esa.pfa.classifier;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * @author marcoz
 */
public class LocalDatabaseManager implements DatabaseManager {

    private final URI uri;
    private final String[] availableDatabases;

    public LocalDatabaseManager(URI uri) throws IOException {
        this.uri = uri;
        availableDatabases = loadAvailableDatabases(Paths.get(uri));
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public String[] listDatabases() {
        return availableDatabases;
    }

    @Override
    public LocalClassifierManager createClassifierManager(String databaseName) throws IOException {
        Path basePath = Paths.get(uri);
        Path dbPath = basePath.resolve(databaseName);
        if (Files.isDirectory(dbPath)) {
            return new LocalClassifierManager(databaseName, dbPath);
        }
        if (Files.exists(basePath.resolve("ds-descriptor.xml"))) {
            return new LocalClassifierManager(databaseName, basePath);
        }
        throw new IllegalArgumentException("Can not find database with name: " + databaseName);
    }

    private static String[] loadAvailableDatabases(Path auxPath) throws IOException {
        // look for dataset descriptors
        if (Files.exists(auxPath.resolve("ds-descriptor.xml"))) {
            return new String[]{"Default"};
        } else {
            final List<String> databaseList = new ArrayList<>();
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(auxPath)) {
                for (Path path : directoryStream) {
                    if (Files.isDirectory(path)) {
                        if (Files.exists(path.resolve("ds-descriptor.xml"))) {
                            databaseList.add(path.getFileName().toString());
                        }
                    }
                }
            }
            return databaseList.toArray(new String[databaseList.size()]);
        }
    }
}
