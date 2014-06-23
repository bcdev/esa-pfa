package org.esa.pfa.fe.op.out;

import com.bc.ceres.binding.PropertySet;
import org.esa.beam.framework.datamodel.Product;
import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.fe.op.Patch;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * @author Norman Fomferra
 */
public class DefaultPatchWriter implements PatchWriter {

    private static final String PRODUCT_DIR_NAME_EXTENSION = ".fex";

    private final Path productTargetDirPath;
    private final PatchWriter[] patchWriters;
    private boolean skipProductOutput;
    private final FileSystem zipFileSystem;

    public DefaultPatchWriter(PatchWriterFactory patchWriterFactory, Product product) throws IOException {
        String targetPathString = patchWriterFactory.getTargetPath();
        if (targetPathString == null) {
            targetPathString = ".";
        }

        final Path path = Paths.get(targetPathString);
        final boolean overwriteMode = patchWriterFactory.isOverwriteMode();
        if (Files.exists(path)) {
            if (!overwriteMode) {
                final Stream<Path> contents = Files.list(path);
                if (contents != null && contents.count() > 0) {
                    throw new IOException(String.format("Directory is not empty: '%s'.", path));
                }
            }
        } else {
            if (!overwriteMode) {
                throw new IOException(String.format("Directory does not exist: '%s'.", path));
            } else {
                try {
                    Files.createDirectory(path);
                } catch (FileAlreadyExistsException e) {
                    throw new IOException(String.format("Failed to create directory '%s'.", path), e);
                }
            }
        }

        final boolean zipAllOutput = patchWriterFactory.getZipAllOutput();
        final Path productTargetDirPath;
        if (zipAllOutput) {
            zipFileSystem = createZipFileSystem(targetPathString, product);
            productTargetDirPath = zipFileSystem.getPath("/");
        } else {
            zipFileSystem = null;
            productTargetDirPath = Paths.get(targetPathString, product.getName() + PRODUCT_DIR_NAME_EXTENSION);
            if (!Files.exists(productTargetDirPath)) {
                try {
                    Files.createDirectory(productTargetDirPath);
                } catch (FileAlreadyExistsException e) {
                    throw new IOException(String.format("Failed to create directory '%s'", productTargetDirPath), e);
                }
            }
        }
        this.productTargetDirPath = productTargetDirPath;

        patchWriters = new PatchWriter[]{
                new PropertiesPatchWriter(productTargetDirPath),
                new CsvPatchWriter(productTargetDirPath),
                new HtmlPatchWriter(productTargetDirPath),
                new XmlPatchWriter(productTargetDirPath),
                new KmlPatchWriter(productTargetDirPath),
        };
    }

    @Override
    public void initialize(PropertySet configuration, Product sourceProduct, FeatureType... featureTypes) throws
                                                                                                          IOException {
        for (PatchWriter patchWriter : patchWriters) {
            patchWriter.initialize(configuration, sourceProduct, featureTypes);
        }
        skipProductOutput = configuration.getValue(PatchWriterFactory.PROPERTY_SKIP_PRODUCT_OUTPUT);
    }


    @Override
    public void writePatch(Patch patch, Feature... features) throws IOException {
        final Path patchTargetPath = productTargetDirPath.getFileSystem().getPath(productTargetDirPath.toString(),
                                                                                  patch.getPatchName());
        if (!Files.exists(patchTargetPath)) {
            try {
                Files.createDirectory(patchTargetPath);
            } catch (FileAlreadyExistsException e) {
                throw new IOException(String.format("Failed to create directory '%s'", patchTargetPath), e);
            }
        }

        for (Feature feature : features) {
            final FeatureWriter featureWriter = feature.getExtension(FeatureWriter.class);
            if (featureWriter != null) {
                featureWriter.writeFeature(feature, patchTargetPath);
            }
        }
        for (PatchWriter patchWriter : patchWriters) {
            patchWriter.writePatch(patch, features);
        }
    }

    @Override
    public void close() throws IOException {
        IOException firstIoe = null;
        for (PatchWriter patchWriter : patchWriters) {
            try {
                patchWriter.close();
            } catch (IOException e) {
                if (firstIoe == null) {
                    firstIoe = e;
                }
            }
        }
        if (zipFileSystem != null) {
            zipFileSystem.close();
        }
        if (firstIoe != null) {
            throw firstIoe;
        }
    }

    private static FileSystem createZipFileSystem(String targetPathString, Product product) throws IOException {
        final Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        final Path zipFilePath = Paths.get(targetPathString,
                                           product.getName() + PRODUCT_DIR_NAME_EXTENSION + ".zip");
        final URI uri = URI.create("jar:file:" + zipFilePath.toString());
        return FileSystems.newFileSystem(uri, env);
    }
}
