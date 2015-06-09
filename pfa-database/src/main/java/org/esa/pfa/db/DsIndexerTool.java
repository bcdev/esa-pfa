package org.esa.pfa.db;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.NumericUtils;
import org.esa.pfa.fe.op.DatasetDescriptor;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Properties;

/**
 * The PFA Dataset Indexer Tool.
 *
 * @author Norman
 */
public class DsIndexerTool {

    public static final String DEFAULT_INDEX_NAME = "lucene-index";

    static final PrintWriter PW = new PrintWriter(new OutputStreamWriter(System.out), true);

    final Options options;

    // <options>
    private static CommonOptions commonOptions = new CommonOptions();
    private int precisionStep;
    private int maxThreadCount;
    private String indexName;
    // </options>

    // <arguments>
    private File datasetDir;
    // </arguments>

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        try {
            System.exit(new DsIndexerTool().run(args));
        } catch (Exception e) {
            commonOptions.printError(e);
            System.exit(1);
        }
    }



    public DsIndexerTool() {

        precisionStep = NumericUtils.PRECISION_STEP_DEFAULT;
        maxThreadCount = IndexWriterConfig.DEFAULT_MAX_THREAD_STATES;
        indexName = DEFAULT_INDEX_NAME;

        options = new Options();
        CommonOptions.addOptions(options);
        options.addOption(CommonOptions.opt('i', "index-name", 1, "string", String.format("Name of the output index directory. Default is '%s'.", indexName)));
        options.addOption(CommonOptions.opt('t', "max-threads", 1, "int", String.format("Number of threads to use for indexing. Default is %d.", maxThreadCount)));
        options.addOption(CommonOptions.opt('p', "precision-step", 1, "int", String.format("Precision step used for indexing numeric data. " +
                                                                                           "Lower values consume more disk space but speed up searching. " +
                                                                                           "Suitable values are between 1 and 8. Default is %d.", precisionStep)));
    }


    private int run(String[] args) throws Exception {

        if (!parseCommandLine(args)) {
            return 1;
        }

        File dsFile = new File(datasetDir, "ds-descriptor.xml");
        DatasetDescriptor datasetDescriptor = DatasetDescriptor.read(dsFile);

        Directory indexDirectory = FSDirectory.open(new File(datasetDir, indexName));
        IndexWriterConfig config = DsIndexer.createConfig(maxThreadCount, commonOptions.isVerbose());

        try (DsIndexer dsIndexer = new DsIndexer(datasetDescriptor, precisionStep, indexDirectory, config)) {
            long t1 = System.currentTimeMillis();

            processDatasetDir(dsIndexer, datasetDir);

            long t2 = System.currentTimeMillis();
            System.out.println("patches added to index within " + ((t2 - t1) / 1000) + " seconds");
        }
        return 0;
    }

    private boolean parseCommandLine(String[] args) throws ParseException {
        CommandLine commandLine = new PosixParser().parse(options, args);
        commonOptions.configure(commandLine);
        if (commandLine.hasOption("help")) {
            new HelpFormatter().printHelp(PW, 80,
                                          DsIndexerTool.class.getSimpleName() + " [OPTIONS] <dataset-dir>",
                                          "Creates a Lucene index for the feature extraction directory. [OPTIONS] are:",
                                          options, 2, 2, "\n");
            return false;
        }
        if (commandLine.getArgList().size() != 1) {
            new HelpFormatter().printUsage(PW, 80, DsIndexerTool.class.getSimpleName(), options);
            return false;
        }

        datasetDir = new File(commandLine.getArgs()[0]);

        String indexName = commandLine.getOptionValue("index-name");
        if (indexName != null) {
            this.indexName = indexName;
        }

        String precisionStep = commandLine.getOptionValue("precision-step");
        if (precisionStep != null) {
            this.precisionStep = Integer.parseInt(precisionStep);
        }

        String maxThreads = commandLine.getOptionValue("max-threads");
        if (maxThreads != null) {
            this.maxThreadCount = Integer.parseInt(maxThreads);
        }

        return true;
    }

    private void processDatasetDir(DsIndexer dsIndexer, File datasetDir) throws Exception {
        File[] fexDirs = datasetDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() && file.getName().endsWith(".fex");
            }
        });

        if (fexDirs == null || fexDirs.length == 0) {
            throw new Exception("empty dataset directory: " + datasetDir);
        }

        for (File fexDir : fexDirs) {
            processFexDir(dsIndexer, fexDir);
        }
    }

    private boolean processFexDir(DsIndexer dsIndexer, File fexDir) {
        File[] patchDirs = fexDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() && file.getName().startsWith("x");
            }
        });

        if (patchDirs == null || patchDirs.length == 0) {
            System.out.println("warning: no patches in fex directory: " + fexDir);
            return false;
        }

        for (File patchDir : patchDirs) {
            processPatchDir(dsIndexer, patchDir);
        }

        return true;
    }

    private boolean processPatchDir(DsIndexer dsIndexer, File patchDir) {
        File featureFile = new File(patchDir, "features.txt");
        if (!featureFile.exists()) {
            System.out.println("warning: missing features: " + featureFile);
            return false;
        }

        String name = patchDir.getName();
        int patchX = Integer.parseInt(name.substring(1, name.indexOf("y")));
        int patchY = Integer.parseInt(name.substring(name.indexOf("y")+1, name.length()));

        String fexDirName = patchDir.getParentFile().getName();
        String productName = fexDirName.substring(0, fexDirName.length() - 4);

        try {
            addPatchToIndex(dsIndexer, productName, patchX, patchY, featureFile);
        } catch (IOException e) {
            System.out.printf("i/o exception: %s: file: %s%n", e.getMessage(), featureFile);
            return false;
        }
        return true;
    }

    private void addPatchToIndex(DsIndexer dsIndexer, String productName, int patchX, int patchY, File featureFile) throws IOException {
        Properties featureValues = new Properties();
        try (FileReader reader = new FileReader(featureFile)) {
            featureValues.load(reader);
        }
        dsIndexer.addPatchToIndex(productName, patchX, patchY, featureValues);
    }
}
