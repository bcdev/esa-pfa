package org.esa.pfa.fe.op.out;

import org.esa.pfa.fe.op.FeatureType;
import org.esa.snap.core.datamodel.Product;

import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * tility mehtods for {@link PatchWriter}.
 *
 * @author Norman Fomferra
 */
class PatchWriterHelpers {

    public static void copyResource(Class<?> aClass, String resourceName, Path targetDirPath) throws IOException {
        final InputStream is = aClass.getResourceAsStream(resourceName);
        if (is == null) {
            throw new IllegalArgumentException(
                    String.format("resource not found: class %s: resource %s", aClass.getName(), resourceName));
        }
        final Path targetFilePath = targetDirPath.getFileSystem().getPath(targetDirPath.toString(), resourceName);
        if (!Files.exists(targetFilePath)) {
            try {
                try (final OutputStream os = Files.newOutputStream(targetFilePath)) {
                    byte[] bytes = new byte[16 * 1024];
                    int len;
                    while ((len = is.read(bytes)) > 0) {
                        os.write(bytes, 0, len);
                    }
                }
            } finally {
                is.close();
            }
        }
    }

    public static boolean isImageFeatureType(FeatureType featureType) {
        return RenderedImage.class.isAssignableFrom(featureType.getValueType());
    }

    public static boolean isProductFeatureType(FeatureType featureType) {
        return Product.class.isAssignableFrom(featureType.getValueType());
    }
}
