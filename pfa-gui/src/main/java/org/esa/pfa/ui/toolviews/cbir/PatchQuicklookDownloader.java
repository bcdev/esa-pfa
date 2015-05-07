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

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutionException;

class PatchQuicklookDownloader extends SwingWorker<BufferedImage, Void> {

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

    @Override
    protected BufferedImage doInBackground() throws Exception {
        return session.getPatchQuicklook(patch, quickLookName);
    }

    protected void done() {
        try {
            BufferedImage img = get();
            System.out.println("img = " + img);
            patch.setImage(quickLookName, img);
            drawing.update();
            drawing.invalidate();
            drawing.repaint();
        } catch (InterruptedException | ExecutionException e) {
            String location = patch.getPatchName() + " " + quickLookName;
            SystemUtils.LOG.severe("Failed to download or load quicklook " + location + ":" + e.toString());
        }
    }
}
