/*
 * Copyright (C) 2014 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.pfa.db;

import com.bc.ceres.core.Assert;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;
import org.esa.pfa.fe.op.AttributeType;
import org.esa.pfa.fe.op.DatasetDescriptor;
import org.esa.pfa.fe.op.FeatureType;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Create a Lucene index
 */
public class DsIndexer implements AutoCloseable {

    public static final Version LUCENE_VERSION = Version.LUCENE_47;
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

    private final IndexWriter indexWriter;
    private long docID;

    public DsIndexer(DatasetDescriptor dsDescriptor, int precisionStep, Directory indexDirectory, IndexWriterConfig config) throws IOException {
        initIndexableFieldFactories(precisionStep);
        initFeatureFieldFactories(dsDescriptor);
        indexWriter = new IndexWriter(indexDirectory, config);
    }

    public static IndexWriterConfig createConfig(int maxThreadCount, boolean verbose) {
        IndexWriterConfig config = new IndexWriterConfig(LUCENE_VERSION, LUCENE_ANALYZER);
        config.setRAMBufferSizeMB(16);
        config.setMaxThreadStates(maxThreadCount);
        if (verbose) {
            config.setInfoStream(System.out);
        }
        return config;
    }


    public void close() throws IOException {
        indexWriter.close();
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




    public void addPatchToIndex(String productName, int patchX, int patchY, Properties featureValues) throws IOException {

        Document doc = new Document();
        doc.add(new LongField("id", docID, storedLongType));
        doc.add(new TextField("product", productName, Field.Store.YES));
        doc.add(new IntField("px", patchX, storedIntType));
        doc.add(new IntField("py", patchY, storedIntType));
        // 'rnd' is for selecting random subsets
        doc.add(new DoubleField("rnd", Math.random(), unstoredDoubleType));

        // todo - put useful values into 'lat', 'lon', 'time'
        doc.add(new FloatField("lat", -90 + 180 * (float) Math.random(), unstoredFloatType));
        doc.add(new FloatField("lon", -180 + 360 * (float) Math.random(), unstoredFloatType));
        doc.add(new LongField("time", docID, unstoredLongType));

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

    private void initFeatureFieldFactories(DatasetDescriptor dsDescriptor) {
        featureFieldFactories = new ArrayList<>();
        FeatureType[] featureTypes = dsDescriptor.getFeatureTypes();
        for (FeatureType featureType : featureTypes) {
            if (featureType.hasAttributes()) {
                AttributeType[] attributeTypes = featureType.getAttributeTypes();
                for (AttributeType attributeType : attributeTypes) {
                    FeatureFieldFactory featureFieldFactory = getFeatureFieldFactory(featureType.getName() + "." + attributeType.getName(), attributeType);
                    if (featureFieldFactory != null) {
                        featureFieldFactories.add(featureFieldFactory);
                    }
                }
            } else {
                FeatureFieldFactory featureFieldFactory = getFeatureFieldFactory(featureType.getName(), featureType);
                if (featureFieldFactory != null) {
                    featureFieldFactories.add(featureFieldFactory);
                }
            }
        }
    }

    private FeatureFieldFactory getFeatureFieldFactory(String fieldName, AttributeType attributeType) {
        Class<?> valueType = attributeType.getValueType();
        IndexableFieldFactory indexableFieldFactory = indexableFieldFactoryMap.get(valueType);
        if (indexableFieldFactory == null) {
            return null;
        }
        return new FeatureFieldFactory(fieldName, indexableFieldFactory);
    }

    private void initIndexableFieldFactories(int precisionStep) {
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
