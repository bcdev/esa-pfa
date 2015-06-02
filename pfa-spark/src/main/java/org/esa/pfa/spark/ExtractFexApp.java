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

package org.esa.pfa.spark;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.runtime.internal.DirScanner;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.util.NumericUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.esa.snap.framework.gpf.GPF;
import org.esa.snap.framework.gpf.Operator;
import org.esa.snap.framework.gpf.OperatorSpi;
import org.esa.snap.framework.gpf.OperatorSpiRegistry;
import org.esa.snap.framework.gpf.graph.Graph;
import org.esa.snap.framework.gpf.graph.GraphContext;
import org.esa.snap.framework.gpf.graph.GraphException;
import org.esa.snap.framework.gpf.graph.GraphIO;
import org.esa.snap.framework.gpf.graph.GraphProcessor;
import org.esa.snap.framework.gpf.graph.Node;
import org.esa.snap.framework.gpf.graph.NodeContext;
import org.esa.snap.util.SystemUtils;
import org.esa.snap.util.io.FileUtils;
import org.esa.pfa.db.DsIndexer;
import org.esa.pfa.db.DsIndexerTool;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.fe.op.DatasetDescriptor;
import org.esa.pfa.fe.op.FeatureWriterResult;
import org.esa.pfa.fe.op.PatchResult;
import scala.Tuple2;

import javax.media.jai.JAI;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by marcoz on 10.02.15.
 */
public class ExtractFexApp {

    static {
        SystemUtils.init3rdPartyLibs(ExtractFexApp.class);
        JAI.enableDefaultTileCache();
        JAI.getDefaultInstance().getTileCache().setMemoryCapacity(512 * 1024 * 1024);
        JAI.getDefaultInstance().getTileScheduler().setParallelism(1);
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
        exchangeReadOpsInRegistry();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException("applicationName, Input path and output path expected");
        }
        String applicationName = args[0];
        String input = args[1];
        String output = args[2];

        SparkConf sc = new SparkConf().setAppName("Extract Fex Application");
        JavaSparkContext jsc = new JavaSparkContext(sc);
        Job job = Job.getInstance(new Configuration());
        FileInputFormat.setInputPaths(job, input);
//        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        Configuration configuration = job.getConfiguration();


        JavaPairRDD<String, String> productPathRDD = jsc.newAPIHadoopRDD(configuration, PathInputFormat.class, String.class, String.class);


        Function<Tuple2<String, String>, FeatureWriterResult> mapFunction2 = tuple -> processProduct(applicationName, tuple._2(), output);
        JavaRDD<FeatureWriterResult> patchResultJavaRDD = productPathRDD.map(mapFunction2);

        createLuceneIndex(applicationName, patchResultJavaRDD.toLocalIterator(), output);
//        patchResultJavaRDD.saveAsTextFile(output);

        //patchResultJavaRDD.groupBy(groupFunction).sortByKey(comparator).filter(filterFunction);
    }

    private static void createLuceneIndex(String applicationName, Iterator<FeatureWriterResult> patchIterator, String outputDir) throws IOException {
        PFAApplicationDescriptor applicationDescriptor = getApplicationDescriptor(applicationName);
        URI dsURI = applicationDescriptor.getDatasetDescriptorURI();
        DatasetDescriptor datasetDescriptor;
        try (Reader inputStreamReader = new InputStreamReader(dsURI.toURL().openStream())) {
            datasetDescriptor = DatasetDescriptor.read(inputStreamReader);
        }

        File datasetDir = new File(".", DsIndexerTool.DEFAULT_INDEX_NAME);
        try {
            org.apache.lucene.store.Directory indexDirectory = org.apache.lucene.store.FSDirectory.open(datasetDir);
            IndexWriterConfig config = DsIndexer.createConfig(1, true);

            FileSystem fileSystem = FileSystem.get(new Configuration());
            try (DsIndexer dsIndexer = new DsIndexer(datasetDescriptor, NumericUtils.PRECISION_STEP_DEFAULT, indexDirectory, config);
                 Writer csvWriter = new OutputStreamWriter(fileSystem.create(new Path(new Path(outputDir), "features.csv")))) {
                boolean headerWritten = false;
                while (patchIterator.hasNext()) {
                    FeatureWriterResult featureWriterResult = patchIterator.next();
                    String productName = featureWriterResult.getProductName();
                    for (PatchResult patchResult : featureWriterResult.getPatchResults()) {
                        Properties featureProperties = new Properties();
                        try (Reader reader = new StringReader(patchResult.getFeaturesText())) {
                            featureProperties.load(reader);
                        }
                        if (!headerWritten) {
                            csvWriter.write(getHeader(featureProperties));
                            headerWritten = true;
                        }
                        dsIndexer.addPatchToIndex(productName, patchResult.getPatchX(), patchResult.getPatchY(), featureProperties);
                        csvWriter.write(getRecord(productName, patchResult.getPatchX(), patchResult.getPatchY(), featureProperties));
                    }
                }
            }
            zipDirectory(datasetDir, fileSystem.create(new Path(new Path(outputDir), "lucene-index.zip")));
        } finally {
            FileUtils.deleteTree(datasetDir);
        }
    }

    static String getHeader(Properties featureProperties) {
        List<String> featurePropertyNames = new ArrayList<>(featureProperties.stringPropertyNames()); // TODO optimze

        StringBuilder sb = new StringBuilder();
        sb.append("productName\tpatchX\tpatchY");
        for (String featureName : featurePropertyNames) {
            sb.append("\t");
            sb.append(featureName);
        }
        sb.append("\n");
        return sb.toString();
    }

    static String getRecord(String productName, int patchX, int patchY, Properties featureProperties) {
        StringBuilder sb = new StringBuilder();
        sb.append(productName);
        sb.append("\t");
        sb.append(patchX);
        sb.append("\t");
        sb.append(patchY);
        sb.append("\t");

        List<String> featurePropertyNames = new ArrayList<>(featureProperties.stringPropertyNames()); // TODO optimze
        for (String featureName : featurePropertyNames) {
            sb.append("\t");
            sb.append(featureProperties.getProperty(featureName));
        }
        sb.append("\n");
        return sb.toString();
    }

    public static void zipDirectory(File sourceDir, OutputStream outputStream) throws IOException {
        if (!sourceDir.exists()) {
            throw new FileNotFoundException(sourceDir.getPath());
        }

        // Important: First scan, ...
        DirScanner dirScanner = new DirScanner(sourceDir, true, true);
        String[] entryNames = dirScanner.scan();
        //            ... then create new file (avoid including the new ZIP in the ZIP!)
        ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(outputStream));
        zipOutputStream.setMethod(ZipEntry.DEFLATED);

        try {
            for (String entryName : entryNames) {
                ZipEntry zipEntry = new ZipEntry(entryName.replace('\\', '/'));

                File sourceFile = new File(sourceDir, entryName);
                FileInputStream inputStream = new FileInputStream(sourceFile);
                try {
                    zipOutputStream.putNextEntry(zipEntry);
                    copy(inputStream, zipOutputStream);
                    zipOutputStream.closeEntry();
                } finally {
                    inputStream.close();
                }
            }
        } finally {
            zipOutputStream.close();
        }
    }

    public static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        while (true) {
            int n = inputStream.read(buffer);
            if (n > 0) {
                outputStream.write(buffer, 0, n);
            } else if (n < buffer.length) {
                break;
            }
        }
    }

    static FeatureWriterResult processProduct(String applicationName, String productPath, String targetDir) throws IOException {
        System.out.println("applicationName = [" + applicationName + "], productPath = [" + productPath + "]");

        PFAApplicationDescriptor applicationDescriptor = getApplicationDescriptor(applicationName);
        File tempDir = Files.createTempDirectory(null).toFile();
        try {
            Map<String, String> variables = new HashMap<>();
            variables.put("sourcePath", productPath);
            variables.put("targetDir", tempDir.getAbsolutePath());

            Graph graph = getGraph(applicationDescriptor, variables);
            FeatureWriterResult featureWriterResult = processGraph(graph, applicationDescriptor);

            copyFezToTargetDir(tempDir, targetDir);
            return featureWriterResult;
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Failed to process product " + productPath, e);
        } finally {
            FileUtils.deleteTree(tempDir);
        }
    }

    static PFAApplicationDescriptor getApplicationDescriptor(String applicationName) {
        PFAApplicationRegistry registry = PFAApplicationRegistry.getInstance();
        PFAApplicationDescriptor descriptor = registry.getDescriptorByName(applicationName);
        if (descriptor == null) {
            String msg = String.format("No descriptor with name '%s' available.", applicationName);
            throw new IllegalArgumentException(msg);
        }
        return descriptor;
    }

    private static void copyFezToTargetDir(File targetDir, String outputDir) throws IOException, InterruptedException {
        FileSystem fileSystem = FileSystem.get(new Configuration());
        for (File fezFile : targetDir.listFiles((file) -> file.getName().endsWith(".fex.zip"))) {
            Path outputFilePath = new Path(new Path(outputDir), fezFile.getName());
            try (OutputStream os = fileSystem.create(outputFilePath)) {
                Files.copy(fezFile.toPath(), os);
            }
        }
    }

    private static void exchangeReadOpsInRegistry() {
        OperatorSpiRegistry operatorRegistry = GPF.getDefaultInstance().getOperatorSpiRegistry();
        OperatorSpi originalReadSpi = operatorRegistry.getOperatorSpi("Read");
        if (originalReadSpi != null) {
            // maybe not needed
            operatorRegistry.removeOperatorSpi(originalReadSpi);
        }
        operatorRegistry.addOperatorSpi(new HadoopReadOp.Spi());
    }

    private static FeatureWriterResult processGraph(Graph graph,
                                                    PFAApplicationDescriptor applicationDescriptore) throws IOException, GraphException, InterruptedException {

        GraphContext graphContext = new GraphContext(graph);
        try {
            new GraphProcessor().executeGraph(graphContext, ProgressMonitor.NULL);
            Node fexOpNode = graph.getNode(applicationDescriptore.getFeatureWriterNodeName());
            NodeContext fexOpNodeCtx = graphContext.getNodeContext(fexOpNode);
            Operator fexOp = fexOpNodeCtx.getOperator();

            Object targetProperty = fexOp.getTargetProperty(applicationDescriptore.getFeatureWriterPropertyName());
            if (targetProperty instanceof FeatureWriterResult) {
                FeatureWriterResult featureWriterResult = (FeatureWriterResult) targetProperty;
//                List<PatchResult> patchResults = featureWriterResult.getPatchResults();
                return featureWriterResult;
            }
        } finally {
            graphContext.dispose();
        }
        return null;
    }

    private static Graph getGraph(PFAApplicationDescriptor applicationDescriptor, Map<String, String> variables) throws IOException, GraphException {
        try (Reader graphReader = new InputStreamReader(applicationDescriptor.getGraphFileAsStream())) {
            return GraphIO.read(graphReader, variables);
        }
    }
}
