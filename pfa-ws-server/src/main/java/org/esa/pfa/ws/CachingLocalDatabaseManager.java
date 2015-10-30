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

import org.esa.pfa.classifier.LocalClassifierManager;
import org.esa.pfa.classifier.LocalDatabaseManager;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * @author marcoz
 */
public class CachingLocalDatabaseManager extends LocalDatabaseManager {

    private static URI dbUriForDelegate;
    private static LocalDatabaseManager instance;


    public static void setDbUri(URI dbUri) {
        CachingLocalDatabaseManager.instance = null;
        CachingLocalDatabaseManager.dbUriForDelegate = dbUri;
    }

    public static synchronized LocalDatabaseManager getInstance() throws IOException {
        if (instance == null) {
            instance = new CachingLocalDatabaseManager(dbUriForDelegate);
        }
        return instance;
    }

    private final Map<String, LocalClassifierManager> cache;

    private CachingLocalDatabaseManager(URI dbUri) throws IOException {
        super(dbUri);
        this.cache = new HashMap<>();
    }

    @Override
    public LocalClassifierManager createClassifierManager(String databaseName) throws IOException {
        synchronized (cache) {
            LocalClassifierManager classifierManager = cache.get(databaseName);
            if (classifierManager == null) {
                classifierManager = super.createClassifierManager(databaseName);
                cache.put(databaseName, classifierManager);
            }
            return classifierManager;
        }
    }
}
