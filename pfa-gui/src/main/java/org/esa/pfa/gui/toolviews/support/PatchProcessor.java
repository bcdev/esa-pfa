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
package org.esa.pfa.gui.toolviews.support;

import com.bc.ceres.binding.dom.DefaultDomElement;
import com.bc.ceres.binding.dom.DomElement;
import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.pfa.fe.op.FeatureWriter;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.gui.search.CBIRSession;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.common.ReadOp;
import org.esa.snap.core.gpf.common.WriteOp;
import org.esa.snap.core.gpf.graph.Graph;
import org.esa.snap.core.gpf.graph.GraphIO;
import org.esa.snap.core.gpf.graph.GraphProcessor;
import org.esa.snap.core.gpf.graph.Node;
import org.esa.snap.core.util.Debug;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.io.FileUtils;
import org.esa.snap.rcp.util.Dialogs;

import javax.imageio.ImageIO;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.CropDescriptor;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Feature extraction
 */
public class PatchProcessor extends ProgressMonitorSwingWorker<Patch, Void> {

    private final Product product;
    private final RenderedImage parentImage;
    private final Rectangle region;
    private final CBIRSession session;

    public PatchProcessor(Component component, Product product, RenderedImage parentImage, Rectangle region, CBIRSession session) {
        super(component, "Processing patch");
        this.product = product;
        this.parentImage = parentImage;
        this.region = region;
        this.session = session;
    }

    @Override
    protected Patch doInBackground(ProgressMonitor pm) throws Exception {
        pm.beginTask("Processing...", 100);
        try {
            final Rectangle productRegion = new Rectangle(parentImage.getWidth(), parentImage.getHeight()).
                    intersection(region);

            final Product subset = FeatureWriter.createSubset(product, productRegion);
            final int patchX = region.x / region.width;
            final int patchY = region.y / region.height;

            final BufferedImage patchImage;
            if (parentImage != null) {
                RenderedOp renderedOp = CropDescriptor.create(parentImage,
                                                              (float) productRegion.getX(),
                                                              (float) productRegion.getY(),
                                                              (float) productRegion.getWidth(),
                                                              (float) productRegion.getHeight(), null);
                patchImage = renderedOp.getAsBufferedImage();
            } else {
                patchImage = ProductUtils.createColorIndexedImage(
                        subset.getBand(ProductUtils.findSuitableQuicklookBandName(subset)),
                        com.bc.ceres.core.ProgressMonitor.NULL);
            }

            final Patch patch = new Patch(product.getName(), patchX, patchY, subset);
            patch.setImage(session.getQuicklookBandName1(), patchImage);
            patch.setLabel(Patch.Label.RELEVANT);

            File tmpDir = new File(SystemUtils.getApplicationDataDir(), "tmp");
            File pfaTmpDir = new File(tmpDir, "pfa");
            final File tmpInFolder = new File(pfaTmpDir, "in" + File.separator + patch.getPatchProduct().getName() + ".fex");
            final File tmpOutFolder = new File(pfaTmpDir, "out" + File.separator + patch.getPatchProduct().getName() + ".fex");
            final File subsetFile = new File(tmpInFolder, patch.getPatchName() + ".dim");
            final WriteOp writeOp = new WriteOp(patch.getPatchProduct(), subsetFile, "BEAM-DIMAP");
            writeOp.setDeleteOutputOnFailure(true);
            writeOp.setWriteEntireTileRows(true);
            writeOp.writeProduct(ProgressMonitor.NULL);

            Reader graphReader = null;
            Graph graph;
            try {
                graphReader = new InputStreamReader(session.getApplicationDescriptor().getGraphFileAsStream());
                graph = GraphIO.read(graphReader, null);
            } finally {
                if(graphReader != null)
                    graphReader.close();
            }
            setIO(graph, subsetFile, tmpOutFolder);

            final GraphProcessor processor = new GraphProcessor();
            processor.executeGraph(graph, pm);

            loadFeatures(patch, tmpOutFolder);

            FileUtils.deleteTree(pfaTmpDir);
            return patch;
        } finally {
            pm.done();
        }
    }

    private void loadFeatures(final Patch patch, final File tmpOutFolder) {
        try {
            final File[] fexDirs = tmpOutFolder.listFiles(file -> file.isDirectory() && file.getName().startsWith(patch.getPatchName()));
            if (fexDirs == null || fexDirs.length == 0) {
                return;
            }

            final File[] patchDirs = fexDirs[0].listFiles(file -> file.isDirectory() && file.getName().startsWith("x"));
            if (patchDirs == null || patchDirs.length == 0) {
                return;
            }

            final File featureFile = new File(patchDirs[0], "features.txt");
            System.out.println("featureFile = " + featureFile);
            System.out.println("featureFile.exists() = " + featureFile.exists());
            patch.readFeatureFile(featureFile, session.getEffectiveFeatureTypes());

            final String[] bandNames = CBIRSession.getInstance().getApplicationDescriptor().getQuicklookFileNames();
            for (String quicklookBandName : bandNames) {
                File imageFile = new File(patchDirs[0], quicklookBandName);
                System.out.println("imageFile = " + imageFile);
                if (imageFile.exists()) {
                    BufferedImage img = ImageIO.read(imageFile);
                    System.out.println("img = " + img);
                    patch.setImage(quicklookBandName, img);
                }
            }
        } catch (IOException ioe) {
            Debug.trace(ioe);
            final String msg = "Error reading features " + patch.getPatchName() + "\n" + ioe.getMessage();
            Dialogs.showError(msg);
        }
    }

    private void setIO(final Graph graph, final File srcFile, final File targetFolder) {
        final String readOperatorAlias = OperatorSpi.getOperatorAlias(ReadOp.class);
        final Node readerNode = findNode(graph, readOperatorAlias);
        if (readerNode != null) {
            final DomElement param = new DefaultDomElement("parameters");
            param.createChild("file").setValue(srcFile.getAbsolutePath());
            readerNode.setConfiguration(param);
        }

        Node[] nodes = graph.getNodes();
        if (nodes.length > 0) {
            Node lastNode = nodes[nodes.length - 1];
            DomElement configuration = lastNode.getConfiguration();
            configuration.getChild("targetDir").setValue(targetFolder.getAbsolutePath());
        }
    }

    private Node findNode(final Graph graph, final String alias) {
        for (Node n : graph.getNodes()) {
            if (n.getOperatorName().equals(alias))
                return n;
        }
        return null;
    }
}