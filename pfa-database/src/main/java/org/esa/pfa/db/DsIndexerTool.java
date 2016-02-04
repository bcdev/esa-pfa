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
import org.esa.snap.core.util.io.CsvReader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Properties;

/**
 * The PFA Dataset Indexer Tool.
 * <p>
 * It creates a Lucene index for {@link org.esa.pfa.fe.op.Feature Features}.
 *
 * @author Norman
 */
public class DsIndexerTool {

    /**
     * The storage type. Either expaned or as a zip.
     */
    public enum FexType {FEX, FEZ}

    public static final String DEFAULT_INDEX_NAME = "lucene-index";
    public static final FexType DEFAULT_FEX_TYPE = FexType.FEZ;

    static final PrintWriter PW = new PrintWriter(new OutputStreamWriter(System.out), true);

    final Options options;

    // <options>
    private static CommonOptions commonOptions = new CommonOptions();
    private int precisionStep;
    private int maxThreadCount;
    private String indexName;
    private FexType fexType;
    private File fexArchive;
    // </options>

    // <arguments>
    private File datasetDir;
    // </arguments>

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        try {
            System.exit(new DsIndexerTool().run(args));
        } catch (RuntimeException e) {
            commonOptions.printError(e);
            e.printStackTrace();
            System.exit(-1);
        } catch (Exception e) {
            commonOptions.printError(e);
            e.printStackTrace();
            System.exit(1);
        }
    }


    public DsIndexerTool() {

        precisionStep = NumericUtils.PRECISION_STEP_DEFAULT;
        maxThreadCount = IndexWriterConfig.DEFAULT_MAX_THREAD_STATES;
        indexName = DEFAULT_INDEX_NAME;
        fexType = DEFAULT_FEX_TYPE;

        options = new Options();
        CommonOptions.addOptions(options);
        options.addOption(CommonOptions.opt('i', "index-name", 1, "string", String.format("Name of the output index directory. Default is '%s'.", indexName)));
        options.addOption(CommonOptions.opt('t', "max-threads", 1, "int", String.format("Number of threads to use for indexing. Default is %d.", maxThreadCount)));
        options.addOption(CommonOptions.opt('p', "precision-step", 1, "int", String.format("Precision step used for indexing numeric data. " +
                                                                                                   "Lower values consume more disk space but speed up searching. " +
                                                                                                   "Suitable values are between 1 and 8. Default is %d.", precisionStep)));
        options.addOption(CommonOptions.opt('f', "fex-type", 1, "string", String.format("Type of FEX. One of (FEX, FEZ). Default is '%s'.", indexName)));
        options.addOption(CommonOptions.opt('d', "fex-archive", 1, "string", "Directory that contains the FEX archive. Default is the <dataset-dir>"));
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

            switch (fexType) {
                case FEX:
                    processFexRoot(dsIndexer, fexArchive);
                    break;
                case FEZ:
                    processFezRoot(dsIndexer, fexArchive);
                    break;
            }

            long t2 = System.currentTimeMillis();
            System.out.println(dsIndexer.getNumDocs()+" patches added to index within " + ((t2 - t1) / 1000) + " seconds");
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

        String fexTypeString = commandLine.getOptionValue("fex-type");
        if (fexTypeString != null) {
            this.fexType = FexType.valueOf(fexTypeString);
        }

        String fexArchiveString = commandLine.getOptionValue("fex-archive");
        if (fexArchiveString != null) {
            this.fexArchive = new File(fexArchiveString);
        } else {
            this.fexArchive = datasetDir;
        }
        return true;
    }

    private void processFexRoot(DsIndexer dsIndexer, File fexArchive) throws Exception {
        File[] fexDirs = fexArchive.listFiles(file -> file.isDirectory() && file.getName().endsWith(".fex"));

        if (fexDirs == null || fexDirs.length == 0) {
            throw new Exception("empty fex archive directory: " + fexArchive);
        }

        for (File fexDir : fexDirs) {
            processFexDir(dsIndexer, fexDir);
        }
    }

    private boolean processFexDir(DsIndexer dsIndexer, File fexDir) {
        File[] patchDirs = fexDir.listFiles(file -> file.isDirectory() && file.getName().startsWith("x"));

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
        int patchX = Integer.parseInt(name.substring(1, 4));
        int patchY = Integer.parseInt(name.substring(5, 8));


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

    private void processFezRoot(DsIndexer dsIndexer, File fexArchive) throws IOException {
        FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                System.out.println("path = " + path);
                String fezName = path.toFile().getName();

                if (fezName.endsWith(".fex.zip")) {
                    String productName = fezName.substring(0, fezName.length() - 4);
                    FileSystem fezFS;
                    try {
                        fezFS = FileSystems.newFileSystem(path, null);
                    } catch (Throwable t) {
                        System.out.println("Failed to get FS." +  t.getMessage());
                        return FileVisitResult.CONTINUE;
                    }

                    Path fexCsvPath = fezFS.getPath("fex-overview.csv");
                    if (!Files.exists(fexCsvPath)) {
                        fexCsvPath = fezFS.getPath(productName, "fex-overview.csv");
                        if (!Files.exists(fexCsvPath)) {
                            return FileVisitResult.CONTINUE;
                        }
                    }

                    try (Reader reader = Files.newBufferedReader(fexCsvPath)) {
                        CsvReader csvReader = new CsvReader(reader, new char[]{'\t'});
                        String[] header = csvReader.readRecord();
                        String[] data = csvReader.readRecord();
                        while (data != null) {
                            Properties featureValues = new Properties();
                            for (int i = 0; i < header.length; i++) {
                                featureValues.setProperty(header[i], data[i]);
                            }

                            String patchName = featureValues.getProperty("patch");
                            if (patchName.length() == 6) {
                                int patchX = Integer.parseInt(patchName.substring(1, 3));
                                int patchY = Integer.parseInt(patchName.substring(4, 6));
                                dsIndexer.addPatchToIndex(productName, patchX, patchY, featureValues);
                            } else if (patchName.length() == 8) {
                                int patchX = Integer.parseInt(patchName.substring(1, 4));
                                int patchY = Integer.parseInt(patchName.substring(5, 8));
                                dsIndexer.addPatchToIndex(productName, patchX, patchY, featureValues);
                            }
                            data = csvReader.readRecord();
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        };
        Files.walkFileTree(Paths.get(fexArchive.getAbsolutePath()), visitor);

    }
}
