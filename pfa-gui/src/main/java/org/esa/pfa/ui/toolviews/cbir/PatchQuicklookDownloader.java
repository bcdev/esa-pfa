/*
 * Copyright (C) 2013 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.pfa.ui.toolviews.cbir;

import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.search.CBIRSession;
import org.esa.snap.util.SystemUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;

class PatchQuicklookDownloader extends SwingWorker {

    private final CBIRSession session;
    private final Patch patch;
    private final String quickLookName;
    private final PatchDrawer.PatchDrawing drawing;

    public PatchQuicklookDownloader(final CBIRSession session, final Patch patch, final String quickLookName,
                                    final PatchDrawer.PatchDrawing drawing) {
        this.session = session;
        this.patch = patch;
        this.quickLookName = quickLookName;
        this.drawing = drawing;
    }

    /**
     * Computes a result, or throws an exception if unable to do so.
     * <p>
     * Note that this method is executed only once.
     * <p>
     * Note: this method is executed in a background thread.
     *
     * @return the computed result
     * @throws Exception if unable to compute a result
     */
    @Override
    protected Object doInBackground() throws Exception {
        try {
            BufferedImage img = session.getPatchQuicklook(patch, quickLookName);
            System.out.println("img = " + img);
            patch.setImage(quickLookName, img);
        } catch (Exception e) {
            String location = patch.getPatchName() + " " + quickLookName;
            SystemUtils.LOG.severe("Failed to download or load quicklook " + location + ":" + e.toString());
        }
        return null;
    }

    protected void done() {
        drawing.update();
        drawing.invalidate();
    }
}
