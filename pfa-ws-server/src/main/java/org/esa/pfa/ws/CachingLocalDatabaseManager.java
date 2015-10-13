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

import org.esa.pfa.classifier.DatabaseManager;
import org.esa.pfa.classifier.LocalClassifierManager;
import org.esa.pfa.classifier.LocalDatabaseManager;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * @author marcoz
 */
public class CachingLocalDatabaseManager implements DatabaseManager {

    private static URI dbUri;
    private static LocalDatabaseManager instance;

    public static void setDbUri(URI dbUri) {
        CachingLocalDatabaseManager.instance = null;
        CachingLocalDatabaseManager.dbUri = dbUri;
    }

    public static synchronized LocalDatabaseManager getInstance() throws IOException {
        if (instance == null) {
            instance = new LocalDatabaseManager(dbUri);
        }
        return instance;
    }

    private final Map<String, LocalClassifierManager> cache = new HashMap<>();

    @Override
    public URI getURI() {
        return instance.getURI();
    }

    @Override
    public String[] listDatabases() {
        return instance.listDatabases();
    }

    @Override
    public LocalClassifierManager createClassifierManager(String databaseName) throws IOException {
        synchronized (cache) {
            LocalClassifierManager localClassifierManager = cache.get(databaseName);
            if (localClassifierManager == null) {
                localClassifierManager = instance.createClassifierManager(databaseName);
                cache.put(databaseName, localClassifierManager);
            }
            return localClassifierManager;
        }
    }
}
