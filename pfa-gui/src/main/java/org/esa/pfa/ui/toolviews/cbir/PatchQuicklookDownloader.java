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
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

class PatchQuicklookDownloader extends SwingWorker<BufferedImage, Void> {

    private final CBIRSession session;
    private final Patch patch;
    private final String quickLookName;
    private final PatchDrawer.PatchDrawing drawing;

    private static final ConcurrentMap<QlKey, BufferedImage> qlCache = new ConcurrentHashMap<>();

    public PatchQuicklookDownloader(final CBIRSession session, final Patch patch, final String quickLookName,
                                    final PatchDrawer.PatchDrawing drawing) {
        this.session = session;
        this.patch = patch;
        this.quickLookName = quickLookName;
        this.drawing = drawing;
    }

    @Override
    protected BufferedImage doInBackground() throws Exception {
        final QlKey qlKey = new QlKey(patch, quickLookName);
        return qlCache.computeIfAbsent(qlKey, qlKey1 -> {
            try {
                return session.getPatchQuicklook(patch, quickLookName);
            } catch (IOException e) {
                SystemUtils.LOG.log(Level.WARNING, "Failed to download " + qlKey, e);
                return null;
            }
        });
    }

    protected void done() {
        try {
            patch.setImage(quickLookName, get());
            drawing.update();
            drawing.invalidate();
            drawing.repaint();
        } catch (InterruptedException | ExecutionException e) {
            SystemUtils.LOG.severe(String.format("Failed to download or load quicklook '%s' at patch %s: %s",
                                                 quickLookName, patch.getPatchName(), e.toString()));
        }
    }

    private static class QlKey {

        private final String quickLookName;
        private final String parentProductName;
        private final int patchX;
        private final int patchY;

        public QlKey(Patch patch, String quickLookName) {
            parentProductName = patch.getParentProductName();
            patchX = patch.getPatchX();
            patchY = patch.getPatchY();
            this.quickLookName = quickLookName;
        }

        @Override
        public String toString() {
            return String.format("Quicklook '%s' for patch x%03dy%03d in %s", quickLookName, patchX, patchY, parentProductName);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            QlKey qlKey = (QlKey) o;

            if (patchX != qlKey.patchX) return false;
            if (patchY != qlKey.patchY) return false;
            if (!quickLookName.equals(qlKey.quickLookName)) return false;
            return parentProductName.equals(qlKey.parentProductName);

        }

        @Override
        public int hashCode() {
            int result = quickLookName.hashCode();
            result = 31 * result + parentProductName.hashCode();
            result = 31 * result + patchX;
            result = 31 * result + patchY;
            return result;
        }
    }
}
