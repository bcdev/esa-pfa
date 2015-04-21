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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Creating a new classifier manager
 */
public class ClassifierManagerFactory {

    public static ClassifierManager create(String parameters) throws IOException {
        Path auxPath = Paths.get(URI.create(parameters));
        Path classifierStoragePath = auxPath.resolve("Classifiers");
        if (!Files.exists(classifierStoragePath)) {
            Files.createDirectories(classifierStoragePath);
        }
        Path patchPath = auxPath;
        Path dbPath = auxPath;
        return new LocalClassifierManager(classifierStoragePath, dbPath, patchPath);
    }

}
