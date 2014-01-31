package org.esa.pfa.db;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.NumericConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.esa.pfa.fe.op.AttributeType;
import org.esa.pfa.fe.op.FeatureType;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * The PFA Dataset Indexer Tool.
 *
 * @author Norman
 */
public class DsSearcher {

    static NumericConfig intNumericConfig = new NumericConfig(8, NumberFormat.getNumberInstance(Locale.ENGLISH), FieldType.NumericType.INT);
    static NumericConfig longNumericConfig = new NumericConfig(8, NumberFormat.getNumberInstance(Locale.ENGLISH), FieldType.NumericType.LONG);
    static NumericConfig floatNumericConfig = new NumericConfig(8, NumberFormat.getNumberInstance(Locale.ENGLISH), FieldType.NumericType.FLOAT);
    static Map<Class<?>, NumericConfig> numericConfigMap;

    int threadCount = 1;
    int maxHitCount = 100;
    DatasetDescriptor dsDescriptor;

    public static void main(String[] args) {
        try {
            new DsSearcher().run(args);
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void run(String[] args) throws Exception {
        if (args.length != 1) {
            throw new Exception("usage: DsSearcher <dataset-dir>");
        }

        String dsPath = args[0];
        File dsDir = new File(dsPath);
        dsDescriptor = DatasetDescriptor.read(new File(dsDir, "ds-descriptor.xml"));

        StandardQueryParser parser = new StandardQueryParser(DsIndexer.ANALYZER);
        parser.setNumericConfigMap(getNumericConfigMap(dsDescriptor));

        try (Directory indexDirectory = new MMapDirectory(new File(dsDir, "lucene-index"))) {
            try (IndexReader indexReader = DirectoryReader.open(indexDirectory)) {
                IndexSearcher indexSearcher = new IndexSearcher(indexReader, Executors.newFixedThreadPool(this.threadCount));
                BufferedReader queryReader = new BufferedReader(new InputStreamReader(System.in));
                String queryExpr;
                do {
                    System.out.print(">>> ");
                    System.out.flush();
                    queryExpr = queryReader.readLine();
                } while (queryExpr != null && processQuery(indexSearcher, parser, queryExpr));
            }
        }

    }

    private boolean processQuery(IndexSearcher indexSearcher, StandardQueryParser parser, String queryExpr) {

        queryExpr = queryExpr.trim();

        if (queryExpr.isEmpty()) {
            return true;
        }

        if (queryExpr.equalsIgnoreCase("help")) {
            printHelp();
            return true;
        }

        if (queryExpr.equalsIgnoreCase("exit") || queryExpr.equalsIgnoreCase("quit")) {
            return false;
        }

        try {
            Query query = parser.parse(queryExpr, "product");

            long t1 = System.currentTimeMillis();
            TopDocs topDocs = indexSearcher.search(query, this.maxHitCount);
            long t2 = System.currentTimeMillis();

            if (topDocs.totalHits == 0) {
                System.out.println("no documents found within " + (t2 - t1) + " ms");
            } else {
                System.out.println("found " + topDocs.totalHits + " documents(s) within " + (t2 - t1) + " ms:");
                ScoreDoc[] scoreDocs = topDocs.scoreDocs;
                for (int i = 0; i < scoreDocs.length; i++) {
                    ScoreDoc scoreDoc = scoreDocs[i];
                    int docID = scoreDoc.doc;
                    Document doc = indexSearcher.doc(docID);
                    String productName = doc.getValues("product")[0];
                    String patchX = doc.getValues("px")[0];
                    String patchY = doc.getValues("py")[0];
                    System.out.printf("[%4d]: product:\"%s\", px:%s, py:%s\n", i + 1, productName, patchX, patchY);
                }
            }
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }

        return true;
    }

    private void printHelp() {
        System.out.println("Searchable Fields:");
        printAttrHelp("product", String.class, "EO data product name");
        printAttrHelp("px", Integer.TYPE, "Patch x-coordinate");
        printAttrHelp("py", Integer.TYPE, "Patch y-coordinate");
        for (FeatureType featureType : dsDescriptor.getFeatureTypes()) {
            if (featureType.hasAttributes()) {
                AttributeType[] attributeTypes = featureType.getAttributeTypes();
                for (AttributeType attributeType : attributeTypes) {
                        printAttrHelp(featureType.getName() + "." + attributeType.getName(), attributeType);
                }
            } else {
                printAttrHelp(featureType.getName(), featureType);
            }
        }
        System.out.println();
        System.out.println("Query Parser Syntax: https://lucene.apache.org/core/4_6_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description");
        System.out.println();
    }

    private void printAttrHelp(String fieldName, AttributeType attributeType) {
        printAttrHelp(fieldName,attributeType.getValueType(), attributeType.getDescription());
    }

    private void printAttrHelp(String fieldName, Class<?> valueType, String description) {
        System.out.printf("  %s: %s  --  %s\n", fieldName, valueType, description);
    }

    private Map<String, NumericConfig> getNumericConfigMap(DatasetDescriptor dsDescriptor) {
        Map<String, NumericConfig> numericConfigMap = new HashMap<>();
        numericConfigMap.put("id", longNumericConfig);
        numericConfigMap.put("px", intNumericConfig);
        numericConfigMap.put("py", intNumericConfig);
        FeatureType[] featureTypes = dsDescriptor.getFeatureTypes();
        for (FeatureType featureType : featureTypes) {
            if (featureType.hasAttributes()) {
                AttributeType[] attributeTypes = featureType.getAttributeTypes();
                for (AttributeType attributeType : attributeTypes) {
                    NumericConfig numericConfig = getNumericConfig(attributeType);
                    if (numericConfig != null) {
                        numericConfigMap.put(featureType.getName() + "." + attributeType.getName(), numericConfig);
                    }
                }
            } else {
                NumericConfig numericConfig = getNumericConfig(featureType);
                if (numericConfig != null) {
                    numericConfigMap.put(featureType.getName(), numericConfig);
                }
            }
        }
        return numericConfigMap;
    }

    private static NumericConfig getNumericConfig(AttributeType attributeType) {
        Class<?> valueType = attributeType.getValueType();
        return numericConfigMap.get(valueType);
    }


    static {
        numericConfigMap = new HashMap<>();

        numericConfigMap.put(Byte.TYPE, intNumericConfig);
        numericConfigMap.put(Byte.class, intNumericConfig);
        numericConfigMap.put(Short.TYPE, intNumericConfig);
        numericConfigMap.put(Short.class, intNumericConfig);
        numericConfigMap.put(Integer.TYPE, intNumericConfig);
        numericConfigMap.put(Integer.class, intNumericConfig);

        numericConfigMap.put(Long.TYPE, longNumericConfig);
        numericConfigMap.put(Long.class, longNumericConfig);

        numericConfigMap.put(Float.TYPE, floatNumericConfig);
        numericConfigMap.put(Float.class, floatNumericConfig);
        numericConfigMap.put(Double.TYPE, floatNumericConfig);
        numericConfigMap.put(Double.class, floatNumericConfig);
    }



}
