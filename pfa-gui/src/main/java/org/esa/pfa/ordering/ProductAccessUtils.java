package org.esa.pfa.ordering;

import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.util.SystemUtils;
import org.esa.snap.util.io.WildcardMatcher;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Created by Norman on 05.06.2015.
 */
public class ProductAccessUtils {

    public static File findLocalFile(String productName) {
        String[] localPaths = ProductAccessOptions.getDefault().getLocalPaths();
        for (String localPath : localPaths) {
            File[] dirs;
            if (localPath.contains("?") || localPath.contains("*")) {
                try {
                    dirs = WildcardMatcher.glob(localPath);
                } catch (IOException e) {
                    dirs = new File[0];
                    SystemUtils.LOG.severe(e.getMessage());
                }
            } else {
                dirs = new File[]{new File(localPath)};
            }
            for (File dir : dirs) {
                File file = new File(dir, productName);
                if (file.exists()) {
                    return file;
                }
            }
        }
        return null;
    }

    public static Product findOpenedProduct(final File productFile) {
        return Arrays.stream(SnapApp.getDefault().getProductManager().getProducts())
                .filter(product -> productFile.equals(product.getFileLocation()))
                .findFirst().get();
    }

    public static Product findOpenedProduct(final String productName) {
        return Arrays.stream(SnapApp.getDefault().getProductManager().getProducts())
                .filter(product -> productName.equals(product.getName()))
                .findFirst().get();
    }
}
