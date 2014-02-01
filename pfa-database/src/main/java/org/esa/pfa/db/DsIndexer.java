package org.esa.pfa.db;

import com.bc.ceres.core.Assert;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.NumericUtils;
import org.apache.lucene.util.Version;
import org.esa.pfa.fe.op.AttributeType;
import org.esa.pfa.fe.op.FeatureType;

import java.io.*;
import java.util.*;

/**
 * The PFA Dataset Indexer Tool.
 *
 * @author Norman
 */
public class DsIndexer {

    public static final Version LUCENE_VERSION = Version.LUCENE_46;
    //public static final Analyzer LUCENE_ANALYZER = new SimpleAnalyzer(LUCENE_VERSION);
    public static final Analyzer LUCENE_ANALYZER = new ProductNameAnalyzer(LUCENE_VERSION);

    private FieldType storedIntType;
    private FieldType storedLongType;
    private FieldType unstoredLongType;
    private FieldType storedFloatType;
    private FieldType unstoredFloatType;
    private FieldType storedDoubleType;
    private FieldType unstoredDoubleType;

    private Map<Class<?>, IndexableFieldFactory> indexableFieldFactoryMap;
    private List<FeatureFieldFactory> featureFieldFactories;

    private IndexWriter indexWriter;
    private long docID;

    // <options>
    private boolean verbose = false;
    private int precisionStep = NumericUtils.PRECISION_STEP_DEFAULT;
    // </options>

    public static void main(String[] args) {
        try {
            new DsIndexer().run(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private interface IndexableFieldFactory {
        IndexableField createIndexableField(String fieldName, String fieldValue, Field.Store fieldStore);
    }

    private static class ProductNameAnalyzer extends Analyzer {

        private Version version;

        private ProductNameAnalyzer(Version version) {
            this.version = version;
        }

        @Override
        protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
            return new TokenStreamComponents(new ProductNameTokenizer(version, reader));
        }
    }

    private static class ProductNameTokenizer extends CharTokenizer {

        public ProductNameTokenizer(Version version, Reader in) {
            super(version, in);
        }

        @Override
        protected int normalize(int c) {
            return Character.toLowerCase(c);
        }

        @Override
        protected boolean isTokenChar(int c) {
            return Character.isLetter(c) || Character.isDigit(c);
        }
    }

    private class FeatureFieldFactory {
        final String fieldName;
        final IndexableFieldFactory indexableFieldFactory;

        public FeatureFieldFactory(String fieldName, IndexableFieldFactory indexableFieldFactory) {
            Assert.notNull(fieldName, "fieldName");
            Assert.notNull(indexableFieldFactory, "indexableFieldFactory");
            this.fieldName = fieldName;
            this.indexableFieldFactory = indexableFieldFactory;
        }

        public String getFieldName() {
            return fieldName;
        }

        public IndexableField createIndexableField(String fieldValue, Field.Store fieldStore) {
            return indexableFieldFactory.createIndexableField(fieldName, fieldValue, fieldStore);
        }
    }

    private void run(String[] args) throws Exception {
        if (args.length != 1) {
            throw new Exception("usage: DsIndexer <dataset-dir>");
        }
        String dsPath = args[0];
        File dsDir = new File(dsPath);
        DatasetDescriptor dsDescriptor = DatasetDescriptor.read(new File(dsDir, "ds-descriptor.xml"));

        initIndexableFieldFactories();

        featureFieldFactories = new ArrayList<>();
        FeatureType[] featureTypes = dsDescriptor.getFeatureTypes();
        for (FeatureType featureType : featureTypes) {
            if (featureType.hasAttributes()) {
                AttributeType[] attributeTypes = featureType.getAttributeTypes();
                for (AttributeType attributeType : attributeTypes) {
                    FeatureFieldFactory featureFieldFactory = getIndexableFieldFactory(featureType.getName() + "." + attributeType.getName(), attributeType);
                    if (featureFieldFactory != null) {
                        featureFieldFactories.add(featureFieldFactory);
                    }
                }
            } else {
                FeatureFieldFactory featureFieldFactory = getIndexableFieldFactory(featureType.getName(), featureType);
                if (featureFieldFactory != null) {
                    featureFieldFactories.add(featureFieldFactory);
                }
            }
        }


        IndexWriterConfig config = new IndexWriterConfig(LUCENE_VERSION, LUCENE_ANALYZER);
        config.setRAMBufferSizeMB(16);
        if (verbose) {
            config.setInfoStream(System.out);
        }

        long t1, t2;

        try (Directory indexDirectory = FSDirectory.open(new File(dsPath, "lucene-index"))) {
            indexWriter = new IndexWriter(indexDirectory, config);
            try {
                t1 = System.currentTimeMillis();
                processDsDir(dsDir);
                t2 = System.currentTimeMillis();
            } finally {
                indexWriter.close();
            }
        }

        System.out.println(docID + "(s) patches added to index within " + ((t2 - t1) / 1000) + " seconds");

    }

    private void processDsDir(File dsDir) throws Exception {
        File[] fexDirs = dsDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() && file.getName().endsWith(".fex");
            }
        });

        if (fexDirs == null || fexDirs.length == 0) {
            throw new Exception("empty dataset directory: " + dsDir);
        }

        for (File fexDir : fexDirs) {
            processFexDir(fexDir);
        }
    }

    private boolean processFexDir(File fexDir) {
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
            processPatchDir(patchDir);
        }

        return true;
    }

    private boolean processPatchDir(File patchDir) {
        File featureFile = new File(patchDir, "features.txt");
        if (!featureFile.exists()) {
            System.out.println("warning: missing features: " + featureFile);
            return false;
        }

        String name = patchDir.getName();
        int patchX = Integer.parseInt(name.substring(1, 3));
        int patchY = Integer.parseInt(name.substring(4, 6));

        String fexDirName = patchDir.getParentFile().getName();
        String productName = fexDirName.substring(0, fexDirName.length() - 4);

        try {
            addPatchToIndex(productName, patchX, patchY, featureFile);
        } catch (IOException e) {
            System.out.printf("i/o exception: %s: file: %s%n", e.getMessage(), featureFile);
            return false;
        }
        return true;
    }

    private void addPatchToIndex(String productName, int patchX, int patchY, File featureFile) throws IOException {

        Properties featureValues = new Properties();
        try (FileReader reader = new FileReader(featureFile)) {
            featureValues.load(reader);
        }

        Document doc = new Document();
        doc.add(new TextField("product", productName, Field.Store.YES));
        doc.add(new LongField("id", docID, storedLongType));
        doc.add(new IntField("no", (int)docID, storedIntType));
        doc.add(new IntField("px", patchX, storedIntType));
        doc.add(new IntField("py", patchY, storedIntType));
        // 'rnd' is for selecting random subsets
        doc.add(new DoubleField("rnd", Math.random(), storedDoubleType));
        //doc.add(new DoubleField("rnd", Math.random(), unstoredDoubleType));
        // todo - put useful values into 'lat', 'lon', 'time'
        doc.add(new FloatField("lat", -90 + 180 * (float) Math.random(), unstoredFloatType));
        doc.add(new FloatField("lon", -180 + 360 * (float) Math.random(), unstoredFloatType));
        //doc.add(new LongField("time", docID, unstoredLongType));

        for (FeatureFieldFactory featureFieldFactory : featureFieldFactories) {
            String fieldName = featureFieldFactory.getFieldName();
            String fieldValue = featureValues.getProperty(fieldName);
            if (fieldValue != null) {
                IndexableField indexableField = featureFieldFactory.createIndexableField(fieldValue, Field.Store.YES);
                doc.add(indexableField);
            }
        }

        indexWriter.addDocument(doc);
        System.out.printf("[%5d]: product:\"%s\", px:%d, py:%d\n", docID, productName, patchX, patchY);
        docID++;
    }


    private FeatureFieldFactory getIndexableFieldFactory(String fieldName, AttributeType attributeType) {
        Class<?> valueType = attributeType.getValueType();
        IndexableFieldFactory indexableFieldFactory = indexableFieldFactoryMap.get(valueType);
        if (indexableFieldFactory == null) {
            return null;
        }
        return new FeatureFieldFactory(fieldName, indexableFieldFactory);
    }


    private void initIndexableFieldFactories() {
        storedIntType = createNumericFieldType(FieldType.NumericType.INT, Field.Store.YES, precisionStep);
        storedLongType = createNumericFieldType(FieldType.NumericType.LONG, Field.Store.YES, precisionStep);
        unstoredLongType = createNumericFieldType(FieldType.NumericType.LONG, Field.Store.NO, precisionStep);
        storedFloatType = createNumericFieldType(FieldType.NumericType.FLOAT, Field.Store.YES, precisionStep);
        unstoredFloatType = createNumericFieldType(FieldType.NumericType.FLOAT, Field.Store.NO, precisionStep);
        storedDoubleType = createNumericFieldType(FieldType.NumericType.DOUBLE, Field.Store.YES, precisionStep);
        unstoredDoubleType = createNumericFieldType(FieldType.NumericType.DOUBLE, Field.Store.NO, precisionStep);

        indexableFieldFactoryMap = new HashMap<>();

        IndexableFieldFactory intIndexableFieldFactory = new IndexableFieldFactory() {
            @Override
            public IndexableField createIndexableField(String fieldName, String fieldValue, Field.Store fieldStore) {
                int value = Integer.parseInt(fieldValue);
                return new IntField(fieldName, value, fieldStore);
            }
        };
        indexableFieldFactoryMap.put(Byte.TYPE, intIndexableFieldFactory);
        indexableFieldFactoryMap.put(Byte.class, intIndexableFieldFactory);
        indexableFieldFactoryMap.put(Short.TYPE, intIndexableFieldFactory);
        indexableFieldFactoryMap.put(Short.class, intIndexableFieldFactory);
        indexableFieldFactoryMap.put(Integer.TYPE, intIndexableFieldFactory);
        indexableFieldFactoryMap.put(Integer.class, intIndexableFieldFactory);

        IndexableFieldFactory longIndexableFieldFactory = new IndexableFieldFactory() {
            @Override
            public IndexableField createIndexableField(String fieldName, String fieldValue, Field.Store fieldStore) {
                long value = Long.parseLong(fieldValue);
                return new LongField(fieldName, value, fieldStore);
            }
        };
        indexableFieldFactoryMap.put(Long.TYPE, longIndexableFieldFactory);
        indexableFieldFactoryMap.put(Long.class, longIndexableFieldFactory);

        IndexableFieldFactory floatIndexableFieldFactory = new IndexableFieldFactory() {
            @Override
            public IndexableField createIndexableField(String fieldName, String fieldValue, Field.Store fieldStore) {
                float value = (float) Double.parseDouble(fieldValue);
                //System.out.println(">> " + fieldName + " = " + value);
                return new FloatField(fieldName, value, fieldStore == Field.Store.YES ? storedFloatType : unstoredFloatType);
            }
        };
        indexableFieldFactoryMap.put(Float.TYPE, floatIndexableFieldFactory);
        indexableFieldFactoryMap.put(Float.class, floatIndexableFieldFactory);
        indexableFieldFactoryMap.put(Double.TYPE, floatIndexableFieldFactory);
        indexableFieldFactoryMap.put(Double.class, floatIndexableFieldFactory);

        IndexableFieldFactory stringIndexableFieldFactory = new IndexableFieldFactory() {
            @Override
            public IndexableField createIndexableField(String fieldName, String fieldValue, Field.Store fieldStore) {
                return new StringField(fieldName, fieldValue, fieldStore);
            }
        };
        indexableFieldFactoryMap.put(String.class, stringIndexableFieldFactory);
        indexableFieldFactoryMap.put(File.class, stringIndexableFieldFactory);
    }

    /**
     * Allow to create numeric fields with variable precision step. All other field properties are defaults (see Lucene code).
     */
    private static FieldType createNumericFieldType(FieldType.NumericType numericType, Field.Store store, int precisionStep) {
        final FieldType type = new FieldType();
        type.setIndexed(true);
        type.setTokenized(true);
        type.setOmitNorms(true);
        type.setIndexOptions(FieldInfo.IndexOptions.DOCS_ONLY);
        type.setNumericType(numericType);
        type.setStored(store == Field.Store.YES);
        type.setNumericPrecisionStep(precisionStep);
        type.freeze();
        return type;
    }
}
