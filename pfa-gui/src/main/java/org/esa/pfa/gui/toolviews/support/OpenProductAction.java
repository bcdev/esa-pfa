package org.esa.pfa.gui.toolviews.support;

import org.esa.pfa.gui.ordering.ProductAccessUtils;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.rcp.util.Dialogs;
import org.netbeans.api.progress.ProgressUtils;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * @author Norman Fomferra
 */
public class OpenProductAction extends AbstractAction {
    final File productFile;

    public OpenProductAction(File productFile) {
        super("Open Product");
        this.productFile = productFile;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        openProduct(productFile);
    }

    public static Product openProduct(final File productFile) {

        Product product = ProductAccessUtils.findOpenedProduct(productFile);
        if (product != null) {
            return product;
        }

        AtomicReference<Product> returnValue = new AtomicReference<>();
        Runnable operation = () -> {
            try {
                returnValue.set(ProductIO.readProduct(productFile));
            } catch (IOException e) {
                SystemUtils.LOG.log(Level.SEVERE, "Failed to open product.", e);
                Dialogs.showError("Failed to open product.");
            }
        };
        ProgressUtils.runOffEventDispatchThread(operation, "Open Product", new AtomicBoolean(), false, 100, 2000);

        return returnValue.get();
    }

}
