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

package org.esa.pfa;

import com.bc.ceres.binding.dom.DefaultDomElement;
import com.bc.ceres.binding.dom.DomElement;
import com.bc.ceres.core.PrintWriterProgressMonitor;
import com.bc.ceres.core.ProgressMonitor;
import org.esa.pfa.classifier.Classifier;
import org.esa.pfa.classifier.ClassifierManager;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.FeatureWriter;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.search.CBIRSession;
import org.esa.snap.framework.dataio.ProductIO;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.gpf.GPF;
import org.esa.snap.framework.gpf.OperatorSpi;
import org.esa.snap.framework.gpf.OperatorSpiRegistry;
import org.esa.snap.framework.gpf.graph.Graph;
import org.esa.snap.framework.gpf.graph.GraphException;
import org.esa.snap.framework.gpf.graph.GraphIO;
import org.esa.snap.framework.gpf.graph.GraphProcessor;
import org.esa.snap.framework.gpf.graph.Node;
import org.esa.snap.gpf.operators.standard.ReadOp;
import org.esa.snap.gpf.operators.standard.WriteOp;
import org.esa.snap.rcp.SnapDialogs;
import org.esa.snap.util.Debug;
import org.esa.snap.util.SystemUtils;

import java.awt.*;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;

/**
 * Created by marcoz on 20.04.15.
 */
public class Workflow {

    public static void main(String[] args) throws Exception {
        String dir = "/home/marcoz/Scratch/pfa/output-snap";//args[0];
        URI uri = new File(dir).toURI();
        System.out.println("uri = " + uri);

        String classifierName = "workflowTest";//args[1];
        System.out.println("classifierName = " + classifierName);

        String appName = "Algal Bloom Detection";//args[2];
        System.out.println("appName = " + appName);

        String queryProductFile = "/home/marcoz/Scratch/pfa/input/MER_RR__1PRACR20100923_052600_000020872093_00134_44777_0000.N1";// args[3]
        System.out.println("queryProductFile = " + queryProductFile);

        System.out.println();
        System.out.println();


        Product product = ProductIO.readProduct(queryProductFile);
        System.out.println("product = " + product);

        PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
        PFAApplicationDescriptor appDesc = applicationRegistry.getDescriptorByName(appName);

        CBIRSession session = CBIRSession.getInstance();
        ClassifierManager classifierManager = session.createClassifierManager(uri.toString(), appDesc.getId());
        System.out.println("classifierManager = " + classifierManager);


        session.createClassifier(classifierName);
        Classifier classifier = session.getClassifier();
        System.out.println("classifier = " + classifier);

        OperatorSpiRegistry operatorSpiRegistry = GPF.getDefaultInstance().getOperatorSpiRegistry();
        operatorSpiRegistry.loadOperatorSpis();

        Patch queryPatch = processPatch(product, new Rectangle(100, 5600, 200, 200), session);

        int numFeaturesQueryPatch = queryPatch.getFeatures().length;
        System.out.println("numFeaturesQueryPatch = " + numFeaturesQueryPatch);

        Feature[] features = queryPatch.getFeatures();
        for (Feature feature : features) {
            System.out.println("feature = " + feature.getName() + " = " + feature.getValue());
        }


        session.startTraining(new Patch[]{queryPatch}, new PrintWriterProgressMonitor(System.out));

        Patch[] relevantTrainingImages = session.getRelevantTrainingImages();
        System.out.println("relevantTrainingImages.length = " + relevantTrainingImages.length);

        Patch[] irrelevantTrainingImages = session.getIrrelevantTrainingImages();
        System.out.println("irrelevantTrainingImages.length = " + irrelevantTrainingImages.length);

        System.exit(0);

//        classifierManager.delete("classifierName");
//
//        String[] availableClassifier = classifierManager.list();
//
//        /////////////////////////////////////////////////////////
//
//        String classifierName = classifier.getName();
//
//        Patch[] queryPatches = null;
//        classifier.startTraining(queryPatches, ProgressMonitor.NULL);
//
//        Patch[] mostAmbigousPatches = classifier.getMostAmbigousPatches(ProgressMonitor.NULL);
//        Patch[] labeledPatches = mostAmbigousPatches;
//        classifier.train(labeledPatches, ProgressMonitor.NULL);
//
//        Patch[] bestPatches = classifier.classify();
    }

    private static Patch processPatch(Product product, Rectangle region, CBIRSession session) throws IOException, GraphException {

        final Rectangle productRegion = new Rectangle(product.getSceneRasterWidth(),
                                                      product.getSceneRasterHeight()).intersection(region);

        final org.esa.snap.framework.datamodel.Product subset = FeatureWriter.createSubset(product, productRegion);
        final int patchX = region.x / region.width;
        final int patchY = region.y / region.height;

        final Patch patch = new Patch(product.getName(), patchX, patchY, null, subset);
        patch.setLabel(Patch.Label.RELEVANT);

        final File tmpInFolder = new File(SystemUtils.getApplicationDataDir(),
                                          "tmp" + File.separator + "in" + File.separator + patch.getPatchProduct().getName() + ".fex");
        final File tmpOutFolder = new File(SystemUtils.getApplicationDataDir(),
                                           "tmp" + File.separator + "out" + File.separator + patch.getPatchProduct().getName() + ".fex");
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
            if (graphReader != null)
                graphReader.close();
        }
        setIO(graph, subsetFile, tmpOutFolder);

        final GraphProcessor processor = new GraphProcessor();
        processor.executeGraph(graph, new PrintWriterProgressMonitor(System.out));

        loadFeatures(patch, tmpOutFolder, session.getEffectiveFeatureTypes());
        // TODO remove tmp folders ???
        return patch;
    }

    private static void setIO(final Graph graph, final File srcFile, final File targetFolder) {
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

    private static Node findNode(final Graph graph, final String alias) {
        for (Node n : graph.getNodes()) {
            if (n.getOperatorName().equals(alias))
                return n;
        }
        return null;
    }

    private static void loadFeatures(final Patch patch, final File datasetDir, FeatureType[] effectiveFeatureTypes) {
        System.out.println("datasetDir = " + datasetDir);
        try {
            File fexDir = new File(datasetDir, patch.getPatchName() + ".fex");
            if (!fexDir.exists() || !fexDir.isDirectory()) {
                return;
            }

            System.out.println("fexDir = " + fexDir);

            final File[] patchDirs = fexDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.isDirectory() && file.getName().startsWith("x");
                }
            });
            if (patchDirs == null || patchDirs.length == 0) {
                return;
            }

            final File featureFile = new File(patchDirs[0], "features.txt");
            System.out.println("featureFile = " + featureFile);
            System.out.println("featureFile = " + featureFile.exists());
            patch.readFeatureFile(featureFile, effectiveFeatureTypes);

        } catch (IOException ioe) {
            Debug.trace(ioe);
            final String msg = "Error reading features " + patch.getPatchName() + "\n" + ioe.getMessage();
            SnapDialogs.showError(msg);
        }
    }
}
