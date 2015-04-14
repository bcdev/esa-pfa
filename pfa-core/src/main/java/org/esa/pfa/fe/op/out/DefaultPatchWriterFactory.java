package org.esa.pfa.fe.op.out;

import com.bc.ceres.core.ExtensionFactory;
import com.bc.ceres.core.ExtensionManager;
import org.esa.snap.framework.dataio.ProductIO;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.util.logging.BeamLogManager;
import org.esa.pfa.fe.op.Feature;

import javax.imageio.ImageIO;
import javax.media.jai.operator.FileStoreDescriptor;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Norman Fomferra
 */
public class DefaultPatchWriterFactory extends PatchWriterFactory {

    static {
        ExtensionManager.getInstance().register(Feature.class, new FeatureSinkFactory());
    }

    public static final String IMAGE_FORMAT_NAME = "PNG";
    public static final String IMAGE_FILE_EXT = ".png";

    @Override
    public PatchWriter createPatchWriter(Product sourceProduct) throws IOException {
        return new DefaultPatchWriter(this, sourceProduct);
    }

    private static class FeatureSinkFactory implements ExtensionFactory {

        @Override
        public FeatureSink getExtension(Object object, Class<?> extensionType) {
            Feature feature = (Feature) object;
            Object value = feature.getValue();
            if (value instanceof Product) {
                return new ProductFeatureSink();
            } else if (value instanceof RenderedImage) {
                return new RenderedImageFeatureSink();
            }
            return null;
        }

        @Override
        public Class<?>[] getExtensionTypes() {
            return new Class<?>[]{FeatureSink.class};
        }

        private static class ProductFeatureSink implements FeatureSink {

            @Override
            public String writeFeature(Feature feature, Path targetDirPath) throws IOException {
                try {
                    final File targetDir = targetDirPath.toFile();
                    final String targetFilePathString = new File(targetDir, feature.getName() + ".dim").getPath();
                    final Product patchProduct = (Product) feature.getValue();
                    final long t1 = System.currentTimeMillis();
                    ProductIO.writeProduct(patchProduct, targetFilePathString, "BEAM-DIMAP");
                    final long t2 = System.currentTimeMillis();
                    logInfo(String.format("Written %s (%d ms)", targetFilePathString, t2 - t1));
                    return targetFilePathString;
                } catch (UnsupportedOperationException e) {
                    logWarning("Skipping product output because target file system is not supported.");
                    return null;
                }
            }
        }

        private static class RenderedImageFeatureSink implements FeatureSink {

            @Override
            public String writeFeature(Feature feature, Path targetDirPath) throws IOException {
                final Path targetFilePath = targetDirPath.getFileSystem().getPath(targetDirPath.toString(),
                                                                                  feature.getName() + IMAGE_FILE_EXT);
                final RenderedImage image = (RenderedImage) feature.getValue();
                final long t1 = System.currentTimeMillis();
                writeImage(targetFilePath, image);
                final long t2 = System.currentTimeMillis();
                logInfo(String.format("Written %s (%d ms)", targetFilePath, t2 - t1));
                return targetFilePath.toString();
            }

            private static void writeImage(Path path, RenderedImage image) throws IOException {
                try {
                    final File targetFile = path.toFile();
                    FileStoreDescriptor.create(image, targetFile.getPath(), IMAGE_FORMAT_NAME, null, null, null);
                } catch (UnsupportedOperationException e) {
                    // file system does not support path.toFile() method
                    try (final OutputStream outputStream = Files.newOutputStream(path)) {
                        ImageIO.write(image, IMAGE_FORMAT_NAME, outputStream);
                    }
                }
            }
        }

        private static void logInfo(String msg) {
            BeamLogManager.getSystemLogger().info(msg);
        }

        private static void logWarning(String msg) {
            BeamLogManager.getSystemLogger().warning(msg);
        }

    }
}
