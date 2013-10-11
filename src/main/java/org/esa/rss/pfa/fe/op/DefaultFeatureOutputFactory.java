package org.esa.rss.pfa.fe.op;

import com.bc.ceres.core.ExtensionFactory;
import com.bc.ceres.core.ExtensionManager;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.logging.BeamLogManager;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;

/**
 * @author Norman Fomferra
 */
public class DefaultFeatureOutputFactory extends FeatureOutputFactory {

    static {
        ExtensionManager.getInstance().register(Feature.class, new DefaultFeatureWriterFactory());
    }

    @Override
    public FeatureOutput createFeatureOutput(Product sourceProduct) throws IOException {
        return new DefaultFeatureOutput(this, sourceProduct.getName());
    }

    private static class DefaultFeatureWriterFactory implements ExtensionFactory {
        @Override
        public Object getExtension(Object object, Class<?> extensionType) {
            Feature feature = (Feature) object;
            Object value = feature.getValue();
            if (value instanceof Product) {
                return new ProductFeatureWriter();
            } else if (value instanceof RenderedImage) {
                return new RenderedImageFeatureWriter();
            }
            return null;
        }

        @Override
        public Class<?>[] getExtensionTypes() {
            return new Class<?>[]{FeatureWriter.class};
        }

        private static class ProductFeatureWriter implements FeatureWriter {
            @Override
            public String writeFeature(Feature feature, String dirPath) throws IOException {
                Product patchProduct = (Product) feature.getValue();
                String path = new File(dirPath, feature.getName() + ".dim").getPath();
                BeamLogManager.getSystemLogger().info("Writing " + path);
                ProductIO.writeProduct(patchProduct, path, "BEAM-DIMAP");
                return path;
            }
        }

        private static class RenderedImageFeatureWriter implements FeatureWriter {
            @Override
            public String writeFeature(Feature feature, String dirPath) throws IOException {
                RenderedImage image = (RenderedImage) feature.getValue();
                File output = new File(dirPath, feature.getName() + ".png");
                BeamLogManager.getSystemLogger().info("Writing " + output);
                ImageIO.write(image, "PNG", output);
                return output.getPath();
            }
        }
    }
}
