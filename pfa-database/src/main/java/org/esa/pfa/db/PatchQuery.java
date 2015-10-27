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
import org.esa.pfa.fe.AbstractApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.fe.op.DatasetDescriptor;
import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/**
 * The PFA Dataset Query Tool.
 */
public class PatchQuery implements QueryInterface {

    private static final int maxThreadCount = 1;
    private static final int maxHitCount = 20;
    private static final String defaultField = "product";
    private static final int precisionStep = NumericUtils.PRECISION_STEP_DEFAULT;
    private static final String indexName = DsIndexerTool.DEFAULT_INDEX_NAME;

    private final StandardQueryParser parser;
    private final IndexSearcher indexSearcher;
    private final FeatureType[] effectiveFeatureTypes;

    public PatchQuery(final File datasetDir, DatasetDescriptor dsDescriptor, FeatureType[] effectiveFeatureTypes) throws IOException {
        this.effectiveFeatureTypes = effectiveFeatureTypes;

        parser = new StandardQueryParser(DsIndexer.LUCENE_ANALYZER);
        NumericConfiguration numConf = new NumericConfiguration(precisionStep);
        parser.setNumericConfigMap(numConf.getNumericConfigMap(dsDescriptor));

        //try (Directory indexDirectory = new MMapDirectory(new File(datasetDir, indexName))) {
        //try (Directory indexDirectory = new NIOFSDirectory(new File(datasetDir, indexName))) {
        try (Directory indexDirectory = new SimpleFSDirectory(new File(datasetDir, indexName))) {
            IndexReader indexReader = DirectoryReader.open(indexDirectory);

            indexSearcher = new IndexSearcher(indexReader, Executors.newFixedThreadPool(this.maxThreadCount));
        }
    }

    public Patch[] query(String queryExpr, int hitCount) {
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
                    String productName = doc.getValues("product")[0];
                    if (productName.endsWith(".fex")) {
                        productName = productName.substring(0, productName.length() - 4);
                    }
                    int patchX = Integer.parseInt(doc.getValues("px")[0]);
                    int patchY = Integer.parseInt(doc.getValues("py")[0]);

                    Patch patch = new Patch(productName, patchX, patchY);
                    getFeatures(doc, patch);
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
        return indexSearcher.getIndexReader().numDocs();
    }

    public Patch[] getRandomPatches(final int numPatches) {
        final IndexReader indexReader = indexSearcher.getIndexReader();

        int numDocs = indexReader.numDocs();
//        System.out.println("numDocs = " + numDocs);

//        return getPatchesFullyRandom(numPatches, indexReader, numDocs);
        return getPatchesFromRandomPoint(numPatches, indexReader, numDocs);
    }

    /**
     * fully random but much slower
     */
    private Patch[] getPatchesFullyRandom(int numPatches, IndexReader indexReader, int numDocs) {
        final List<Patch> patchList = new ArrayList<>(numPatches);
        IntStream randomInts = new Random().ints(numPatches, 0, numDocs);
        randomInts.forEach(value -> {
            try {
                Document doc = indexReader.document(value);
                String productName = doc.getValues("product")[0];
                if (productName.endsWith(".fex")) {
                    productName = productName.substring(0, productName.length() - 4);
                }
                int patchX = Integer.parseInt(doc.getValues("px")[0]);
                int patchY = Integer.parseInt(doc.getValues("py")[0]);

                Patch patch = new Patch(productName, patchX, patchY);
                PatchQuery.this.getFeatures(doc, patch);
                patchList.add(patch);
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
            Document doc;
            try {
                doc = indexReader.document(start + i);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }

            String productName = doc.getValues("product")[0];
            if (productName.endsWith(".fex")) {
                productName = productName.substring(0, productName.length() - 4);
            }
            int patchX = Integer.parseInt(doc.getValues("px")[0]);
            int patchY = Integer.parseInt(doc.getValues("py")[0]);

            Patch patch = new Patch(productName, patchX, patchY);
            PatchQuery.this.getFeatures(doc, patch);

            patches[i] = patch;
        }
        return patches;
    }

    public static void main(String[] args) throws IOException {
        Path dbPath = Paths.get(args[0]);
        System.out.println("dbPath = " + dbPath);

        long t1 = System.currentTimeMillis();
        DatasetDescriptor dsDescriptor = DatasetDescriptor.read(new File(dbPath.toFile(), "ds-descriptor.xml"));
        String appName = dsDescriptor.getName();
        PFAApplicationDescriptor applicationDescriptor = PFAApplicationRegistry.getInstance().getDescriptorByName(appName);
        if (applicationDescriptor == null) {
            throw new IOException("Unknown application name " + appName);
        }

        PatchQuery db;
        if (Files.exists(dbPath.resolve("ds-descriptor.xml")) && Files.exists(dbPath.resolve(DsIndexerTool.DEFAULT_INDEX_NAME))) {
            Set<String> defaultFeatureSet = applicationDescriptor.getDefaultFeatureSet();
            FeatureType[] featureTypes = dsDescriptor.getFeatureTypes();
            FeatureType[] effectiveFeatureTypes = AbstractApplicationDescriptor.getEffectiveFeatureTypes(featureTypes, defaultFeatureSet);
            db = new PatchQuery(dbPath.toFile(), dsDescriptor, effectiveFeatureTypes);
        } else {
            throw new IOException();
        }
        long t2 = System.currentTimeMillis();

        int NumRetrievedImages = 50;

        int classifiedImages = 0;
        final List<Patch> relavantImages = new ArrayList<>(NumRetrievedImages);
        while (relavantImages.size() < NumRetrievedImages && classifiedImages < NumRetrievedImages * 100) {
            final Patch[] archivePatches = db.getRandomPatches(NumRetrievedImages * 2);
            classifiedImages += archivePatches.length;
//            al.classify(archivePatches);
            for (int i = 0; i < archivePatches.length && relavantImages.size() < NumRetrievedImages; i++) {
                if (archivePatches[i].getLabel() == Patch.Label.RELEVANT) {
                    relavantImages.add(archivePatches[i]);
                }
            }
        }
        System.out.println("relavantImages            = " + relavantImages.size());
        System.out.println("classifiedImages          = " + classifiedImages);
//        Patch[] randomPatches = db.getRandomPatches(5000);
//        System.out.println("randomPatches.length = " + randomPatches.length);

        long t3 = System.currentTimeMillis();

        long delta1 = t2 - t1;
        long delta2 = t3 - t2;
        System.out.println("delta1 = " + delta1);
        System.out.println("delta2 = " + delta2);
    }
}
