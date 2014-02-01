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
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.NumericUtils;
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
 * The PFA Dataset Query Tool.
 *
 * @author Norman
 */
public class DsQuery {

    NumericConfig intNumericConfig = new NumericConfig(8, NumberFormat.getNumberInstance(Locale.ENGLISH), FieldType.NumericType.INT);
    NumericConfig longNumericConfig = new NumericConfig(8, NumberFormat.getNumberInstance(Locale.ENGLISH), FieldType.NumericType.LONG);
    NumericConfig floatNumericConfig = new NumericConfig(8, NumberFormat.getNumberInstance(Locale.ENGLISH), FieldType.NumericType.FLOAT);
    NumericConfig doubleNumericConfig = new NumericConfig(8, NumberFormat.getNumberInstance(Locale.ENGLISH), FieldType.NumericType.FLOAT);
    Map<Class<?>, NumericConfig> attributeNumericConfigMap;

    // <options>
    int threadCount = 1;
    int maxHitCount = 100;
    int precisionStep = NumericUtils.PRECISION_STEP_DEFAULT;
    String defaultField = "product";
    // </options>

    DatasetDescriptor dsDescriptor;

    public static void main(String[] args) {
        try {
            new DsQuery().run(args);
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void run(String[] args) throws Exception {
        if (args.length != 1) {
            throw new Exception("usage: DsQuery <dataset-dir>");
        }

        String dsPath = args[0];
        File dsDir = new File(dsPath);
        dsDescriptor = DatasetDescriptor.read(new File(dsDir, "ds-descriptor.xml"));

        StandardQueryParser parser = new StandardQueryParser(DsIndexer.LUCENE_ANALYZER);
        parser.setNumericConfigMap(getNumericConfigMap(dsDescriptor));

        //try (Directory indexDirectory = new MMapDirectory(new File(dsDir, "lucene-index"))) {
        //try (Directory indexDirectory = new NIOFSDirectory(new File(dsDir, "lucene-index"))) {
        try (Directory indexDirectory = new SimpleFSDirectory(new File(dsDir, "lucene-index"))) {
            try (IndexReader indexReader = DirectoryReader.open(indexDirectory)) {
                IndexSearcher indexSearcher = new IndexSearcher(indexReader, Executors.newFixedThreadPool(this.threadCount));
                BufferedReader queryReader = new BufferedReader(new InputStreamReader(System.in));

                System.out.println("Type 'help' for help, type 'exit' or 'quit' to leave.");
                System.out.flush();

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

        if (queryExpr.contains("=")) {
            String[] split = queryExpr.split("=");
            if (split[0].trim().equals("default")) {
                defaultField = split[1].trim();
                System.out.println("Default field set to '" + defaultField + "'");
            }
            return true;
        }

        try {
            Query query = parser.parse(queryExpr, defaultField);

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
                    System.out.printf("[%5d]: product:\"%s\", px:%s, py:%s\n", i + 1, productName, patchX, patchY);
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
        System.out.println("Query Parser Syntax: <field>:<term> | <field>:\"<phrase>\" | <field>:[<n1> TO <n2>]");
        System.out.println("If you omit '<field>:' the default field is used which is '" + defaultField + "'.");
        System.out.println("You can change the default field by typing 'default=<field>'.");
        System.out.println("Multiple queries are ORed, you can otherwise combine them using AND, OR, NOT, +, -.");
        System.out.println("See https://lucene.apache.org/core/4_6_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description");
        System.out.println();
    }

    private void printAttrHelp(String fieldName, AttributeType attributeType) {
        printAttrHelp(fieldName, attributeType.getValueType(), attributeType.getDescription());
    }

    private void printAttrHelp(String fieldName, Class<?> valueType, String description) {
        System.out.printf("  %s: %s  --  %s\n", fieldName, valueType, description);
    }

    private Map<String, NumericConfig> getNumericConfigMap(DatasetDescriptor dsDescriptor) {
        initAttributeNumericConfig();
        Map<String, NumericConfig> numericConfigMap = new HashMap<>();
        numericConfigMap.put("id", longNumericConfig);
        numericConfigMap.put("px", intNumericConfig);
        numericConfigMap.put("py", intNumericConfig);
        numericConfigMap.put("rnd", doubleNumericConfig);
        numericConfigMap.put("lat", floatNumericConfig);
        numericConfigMap.put("lon", floatNumericConfig);
        numericConfigMap.put("time", longNumericConfig);
        addAttributeNumericConfigs(dsDescriptor, numericConfigMap);
        return numericConfigMap;
    }

    private void addAttributeNumericConfigs(DatasetDescriptor dsDescriptor, Map<String, NumericConfig> numericConfigMap) {
        FeatureType[] featureTypes = dsDescriptor.getFeatureTypes();
        for (FeatureType featureType : featureTypes) {
            if (featureType.hasAttributes()) {
                AttributeType[] attributeTypes = featureType.getAttributeTypes();
                for (AttributeType attributeType : attributeTypes) {
                    NumericConfig numericConfig = getAttributeNumericConfig(attributeType);
                    if (numericConfig != null) {
                        numericConfigMap.put(featureType.getName() + "." + attributeType.getName(), numericConfig);
                    }
                }
            } else {
                NumericConfig numericConfig = getAttributeNumericConfig(featureType);
                if (numericConfig != null) {
                    numericConfigMap.put(featureType.getName(), numericConfig);
                }
            }
        }
    }

    private NumericConfig getAttributeNumericConfig(AttributeType attributeType) {
        Class<?> valueType = attributeType.getValueType();
        return attributeNumericConfigMap.get(valueType);
    }


    void initAttributeNumericConfig() {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.ENGLISH);
        intNumericConfig = new NumericConfig(precisionStep, numberFormat, FieldType.NumericType.INT);
        longNumericConfig = new NumericConfig(precisionStep, numberFormat, FieldType.NumericType.LONG);
        floatNumericConfig = new NumericConfig(precisionStep, numberFormat, FieldType.NumericType.FLOAT);
        doubleNumericConfig = new NumericConfig(precisionStep, numberFormat, FieldType.NumericType.DOUBLE);

        attributeNumericConfigMap = new HashMap<>();
        attributeNumericConfigMap.put(Byte.TYPE, intNumericConfig);
        attributeNumericConfigMap.put(Byte.class, intNumericConfig);
        attributeNumericConfigMap.put(Short.TYPE, intNumericConfig);
        attributeNumericConfigMap.put(Short.class, intNumericConfig);
        attributeNumericConfigMap.put(Integer.TYPE, intNumericConfig);
        attributeNumericConfigMap.put(Integer.class, intNumericConfig);
        attributeNumericConfigMap.put(Long.TYPE, longNumericConfig);
        attributeNumericConfigMap.put(Long.class, longNumericConfig);
        attributeNumericConfigMap.put(Float.TYPE, floatNumericConfig);
        attributeNumericConfigMap.put(Float.class, floatNumericConfig);
        // Statistics of features are stored as Double, but Float is sufficient.
        attributeNumericConfigMap.put(Double.TYPE, floatNumericConfig);
        attributeNumericConfigMap.put(Double.class, floatNumericConfig);
    }


}
