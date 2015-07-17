package org.esa.pfa.gui.ordering;

import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.SnapDialogs;
import org.esa.snap.util.SystemUtils;
import org.esa.snap.util.io.WildcardMatcher;
import org.netbeans.api.progress.ProgressUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Norman on 05.06.2015.
 */
public class ProductAccessUtils {

    public static File findLocalFile(String productName, boolean runOffEDT, boolean showError) {
        File productFile;

        if (runOffEDT) {
            AtomicReference<File> returnValue = new AtomicReference<>();
            Runnable operation = () -> {
                returnValue.set(findLocalFile(productName));
            };
            ProgressUtils.runOffEventDispatchThread(operation, "Find Local Product", new AtomicBoolean(), false, 50, 1000);
            productFile = returnValue.get();
        } else {
            productFile = findLocalFile(productName);
        }

        if (productFile == null && showError) {
            SnapDialogs.showError(String.format("A product named '%s'\n" +
                                                        "couldn't be found in any of your local search paths.\n" +
                                                        "(See tab 'ESA PFA' in the Tools / Options dialog.)", productName));
            return null;
        }

        return productFile;
    }

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
                .findFirst().orElse(null);
    }

    public static Product findOpenedProduct(final String productName) {
        return Arrays.stream(SnapApp.getDefault().getProductManager().getProducts())
                .filter(product -> productName.equals(product.getName()))
                .findFirst().orElse(null);
    }

}
