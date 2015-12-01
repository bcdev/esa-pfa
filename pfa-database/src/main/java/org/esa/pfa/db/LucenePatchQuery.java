package org.esa.pfa.db;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.NumericUtils;
import org.esa.pfa.fe.op.DatasetDescriptor;
import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/**
 * The PFA Dataset Query Tool.
 */
public class LucenePatchQuery implements QueryInterface {

    private static final int maxThreadCount = 1;
    private static final int maxHitCount = 20;
    private static final String defaultField = "product";
    private static final int precisionStep = NumericUtils.PRECISION_STEP_DEFAULT;
    private static final String indexName = DsIndexerTool.DEFAULT_INDEX_NAME;

    private final StandardQueryParser parser;
    private final IndexSearcher indexSearcher;
    private final FeatureType[] effectiveFeatureTypes;
    private final IndexReader indexReader;

    public LucenePatchQuery(final File datasetDir, DatasetDescriptor dsDescriptor, FeatureType[] effectiveFeatureTypes) throws IOException {
        this.effectiveFeatureTypes = effectiveFeatureTypes;

        parser = new StandardQueryParser(DsIndexer.LUCENE_ANALYZER);
        NumericConfiguration numConf = new NumericConfiguration(precisionStep);
        parser.setNumericConfigMap(numConf.getNumericConfigMap(dsDescriptor));

        //try (Directory indexDirectory = new MMapDirectory(new File(datasetDir, indexName))) {
        //try (Directory indexDirectory = new NIOFSDirectory(new File(datasetDir, indexName))) {
        try (Directory indexDirectory = new SimpleFSDirectory(new File(datasetDir, indexName))) {
            indexReader = DirectoryReader.open(indexDirectory);
            indexSearcher = new IndexSearcher(indexReader, Executors.newFixedThreadPool(this.maxThreadCount));
        }
    }

    public static boolean isAvailable(Path dbPath) {
        return Files.exists(dbPath.resolve(DsIndexerTool.DEFAULT_INDEX_NAME));
    }

    public Patch[] query(String queryExpr, int hitCount) throws IOException {
        final List<Patch> patchList = new ArrayList<>(100);

        queryExpr = queryExpr.trim();

        try {
            final Query query = parser.parse(queryExpr, defaultField);

            long t1 = System.currentTimeMillis();
            TopDocs topDocs = indexSearcher.search(query, hitCount);
            long t2 = System.currentTimeMillis();

            if (topDocs.totalHits == 0) {
                System.out.println("no documents found within " + (t2 - t1) + " ms");
            } else {
                System.out.println("found " + topDocs.totalHits + " documents(s) within " + (t2 - t1) + " ms:");
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    final Document doc = indexSearcher.doc(scoreDoc.doc);
                    Patch patch = convertDocumentToPatch(doc);
                    patchList.add(patch);
                }
            }
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }

        return patchList.toArray(new Patch[patchList.size()]);
    }

    private void getFeatures(final Document doc, final Patch patch) {
        for (FeatureType feaType : effectiveFeatureTypes) {
            final String[] values = doc.getValues(feaType.getName());
            if (values != null && values.length > 0) {
                patch.addFeature(createFeature(feaType, values[0]));
            }
        }
    }

    private static Feature createFeature(FeatureType feaType, final String value) {
        final Class<?> valueType = feaType.getValueType();

        if (Double.class.isAssignableFrom(valueType)) {
            return new Feature(feaType, Double.parseDouble(value));
        } else if (Float.class.isAssignableFrom(valueType)) {
            return new Feature(feaType, Float.parseFloat(value));
        } else if (Integer.class.isAssignableFrom(valueType)) {
            return new Feature(feaType, Integer.parseInt(value));
        } else if (Boolean.class.isAssignableFrom(valueType)) {
            return new Feature(feaType, Boolean.parseBoolean(value));
        } else if (Character.class.isAssignableFrom(valueType)) {
            return new Feature(feaType, value);
        } else if (String.class.isAssignableFrom(valueType)) {
            return new Feature(feaType, value);
        }
        return null;
    }

    public int getNumPatchesInDatabase() {
        return indexReader.numDocs();
    }

    @Override
    public Patch getPatch(int patchIndex) throws IOException {
        return convertDocumentToPatch(indexReader.document(patchIndex));
    }

    public Patch[] getRandomPatches(final int numPatches) {
        int numDocs = indexReader.numDocs();
        return getPatchesFullyRandom(numPatches, indexReader, numDocs);
//        return getPatchesFromRandomPoint(numPatches, indexReader, numDocs);
    }

    /**
     * fully random but much slower
     */
    private Patch[] getPatchesFullyRandom(int numPatches, IndexReader indexReader, int numDocs) {
        final List<Patch> patchList = new ArrayList<>(numPatches);
        IntStream randomInts = new Random().ints(numPatches, 0, numDocs);
        randomInts.forEach(value -> {
            try {
                patchList.add(convertDocumentToPatch(indexReader.document(value)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return patchList.toArray(new Patch[patchList.size()]);
    }

    private Patch[] getPatchesFromRandomPoint(int numPatches, IndexReader indexReader, int numDocs) {
        int start = new Random().nextInt(numDocs-numPatches);
        Patch[] patches = new Patch[numPatches];
        for (int i = 0; i < patches.length; i++) {
            try {
                patches[i] = convertDocumentToPatch(indexReader.document(start + i));
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return patches;
    }

    private Patch convertDocumentToPatch(Document doc) {
        String productName = doc.getValues("product")[0];
        if (productName.endsWith(".fex")) {
            productName = productName.substring(0, productName.length() - 4);
        }
        int patchX = Integer.parseInt(doc.getValues("px")[0]);
        int patchY = Integer.parseInt(doc.getValues("py")[0]);

        Patch patch = new Patch(productName, patchX, patchY);
        getFeatures(doc, patch);
        // trigger featureValues creation, featureList is not transfered, see org.esa.pfa.ws.RestTransferValue
        patch.getFeatureValues();
        return patch;
    }
}
