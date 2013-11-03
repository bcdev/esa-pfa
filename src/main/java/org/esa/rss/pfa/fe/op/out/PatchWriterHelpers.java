package org.esa.rss.pfa.fe.op.out;

import org.esa.beam.framework.datamodel.Product;
import org.esa.rss.pfa.fe.op.FeatureType;

import java.awt.image.RenderedImage;
import java.io.*;

/**
 * @author Norman Fomferra
 */
class PatchWriterHelpers {
    public static void copyResource(Class<?> aClass, String resourceName, File targetDir) throws IOException {
        InputStream xslIs = aClass.getResourceAsStream(resourceName);
        OutputStream xslOs = new FileOutputStream(new File(targetDir, resourceName));
        byte[] bytes = new byte[16 * 1024];
        int len;
        while ((len = xslIs.read(bytes)) > 0) {
            xslOs.write(bytes, 0, len);
        }
        xslOs.close();
        xslIs.close();
    }

    public static boolean isImageFeatureType(FeatureType featureType) {
        return RenderedImage.class.isAssignableFrom(featureType.getValueType());
    }

    public static boolean isProductFeatureType(FeatureType featureType) {
        return Product.class.isAssignableFrom(featureType.getValueType());
    }
}
