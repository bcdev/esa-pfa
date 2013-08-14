/*
 * Copyright (c) 2013. Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.rss.pfa.fe;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

/**
 * For extracting features from all files in a directory.
 *
 * @author Ralf Quast
 */
public class FexRunner {

    public static void main(String[] args) {
        final String dirPath = args[0];

        final File dir = new File(dirPath);
        if (dir.exists()) {
            final File[] files = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".N1");
                }
            });
            if (files != null) {
                final String[] paths = new String[files.length];
                for (int i = 0; i < files.length; i++) {
                    paths[i] = files[i].getPath();
                }
                final AlgalBloomFex fex = new AlgalBloomFex();
                try {
                    fex.run(paths);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(2);
                }
            }
        }
    }

}
