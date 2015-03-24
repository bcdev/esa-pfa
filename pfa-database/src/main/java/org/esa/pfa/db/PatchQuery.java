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
import org.esa.pfa.fe.op.AttributeType;
import org.esa.pfa.fe.op.DatasetDescriptor;
import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

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

    public PatchQuery(final File datasetDir, Set<String> defaultFeatureSet) throws IOException {
        DatasetDescriptor dsDescriptor = DatasetDescriptor.read(new File(datasetDir, "ds-descriptor.xml"));
        effectiveFeatureTypes = getEffectiveFeatureTypes(dsDescriptor.getFeatureTypes(),
                                                         defaultFeatureSet);
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

    public FeatureType[] getEffectiveFeatureTypes() {
        return effectiveFeatureTypes;
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
                    int patchX = Integer.parseInt(doc.getValues("px")[0]);
                    int patchY = Integer.parseInt(doc.getValues("py")[0]);

                    Patch patch = new Patch(productName, patchX, patchY, null, null);
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

    public Patch[] getRandomPatches(final int numPatches) {
        // todo: remove hard coded query expr.
        return query("product: ENVI*", numPatches);
    }


    public static FeatureType[] getEffectiveFeatureTypes(FeatureType[] featureTypes, Set<String> featureNames) {
        ArrayList<FeatureType> effectiveFeatureTypes = new ArrayList<>();
        for (FeatureType featureType : featureTypes) {
            if (featureType.hasAttributes()) {
                for (AttributeType attrib : featureType.getAttributeTypes()) {
                    final String effectiveName = featureType.getName() + '.' + attrib.getName();
                    if (acceptFeatureTypeName(featureNames, effectiveName)) {
                        FeatureType newFeaType = new FeatureType(effectiveName, attrib.getDescription(), attrib.getValueType());
                        effectiveFeatureTypes.add(newFeaType);
                    }
                }
            } else {
                if (acceptFeatureTypeName(featureNames, featureType.getName())) {
                    effectiveFeatureTypes.add(featureType);
                }
            }
        }
        return effectiveFeatureTypes.toArray(new FeatureType[effectiveFeatureTypes.size()]);
    }

    private static boolean acceptFeatureTypeName(Set<String> allowedNames, String name) {
        return allowedNames == null || allowedNames.contains(name);
    }
}
