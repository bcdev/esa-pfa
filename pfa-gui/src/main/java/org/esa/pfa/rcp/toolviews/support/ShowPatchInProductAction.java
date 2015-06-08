package org.esa.pfa.rcp.toolviews.support;

import com.bc.ceres.core.Assert;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.ordering.ProductAccessUtils;
import org.esa.pfa.search.CBIRSession;
import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.ui.product.ProductSceneView;
import org.esa.snap.netbeans.docwin.WindowUtilities;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.SnapDialogs;
import org.esa.snap.rcp.actions.view.OpenImageViewAction;
import org.esa.snap.rcp.windows.ProductSceneViewTopComponent;
import org.esa.snap.util.Debug;
import org.esa.snap.util.ProductUtils;
import org.netbeans.api.progress.ProgressUtils;

import javax.swing.AbstractAction;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Norman Fomferra
 */
public class ShowPatchInProductAction extends AbstractAction {
    private final File productFile;
    private final Patch patch;

    public ShowPatchInProductAction(String actionName, Patch patch, File productFile) {
        super(actionName);
        this.patch = patch;
        this.productFile = productFile;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            showPatchInProduct(patch, productFile);
        } catch (Exception ioe) {
            Debug.trace(ioe);
            SnapApp.getDefault().handleError("Failed to open data product.", ioe);
        }
    }

    static void showPatchInProduct(final Patch patch, File productFile) throws Exception {

        Product product;

        if (productFile == null) {
            String productName = patch.getParentProductName();
            if (productName == null) {
                return;
            }
            product = ProductAccessUtils.findOpenedProduct(productName);
            if (product == null) {
                productFile = findLocalFile(productName);
                if (productFile == null) {
                    return;
                }
            }
        }

        Assert.state(productFile != null, "productFile != null");
        product = OpenProductAction.openProduct(productFile);
        if (product == null) {
            return;
        }

        ProductSceneViewTopComponent frame = findFrameForProductSceneView(product);
        // First check if we can reuse the currently selected view
        if (frame != null) {
            frame.requestSelected();
            ProductSceneView sceneView = frame.getView();
            Dimension patchDimension = getPatchDimension();
            if (sceneView != null && patchDimension != null) {
                zoomToPatch(sceneView, product, patch, patchDimension);
            }
        } else {
            String bandName = ProductUtils.findSuitableQuicklookBandName(product);
            if (bandName == null) {
                return;
            }
            Band band = product.getBand(bandName);
            if (band == null) {
                return;
            }
            final Dimension patchDimension = getPatchDimension();
            if (patchDimension != null) {
                zoomToPatchOnViewSelected(product, patch, patchDimension);
            }
            new OpenImageViewAction(band).openProductSceneView();
        }
    }

    private static File findLocalFile(String productName) {
        File productFile;AtomicReference<File> returnValue = new AtomicReference<>();
        Runnable operation = () -> {
            returnValue.set(ProductAccessUtils.findLocalFile(productName));
        };
        ProgressUtils.runOffEventDispatchThread(operation, "Find Local Product", new AtomicBoolean(), false, 50, 1000);

        productFile = returnValue.get();
        if (productFile == null) {
            SnapDialogs.showError(String.format("A product named '%s'\n" +
                                                        "couldn't be found in any of your local search paths.\n" +
                                                        "(See tab 'ESA PFA' in the Tools / Options dialog.)", productName));
            return null;
        }
        return productFile;
    }

    private static Dimension getPatchDimension() {
        final Dimension patchDimension;
        PFAApplicationDescriptor applicationDescriptor = CBIRSession.getInstance().getApplicationDescriptor();
        if (applicationDescriptor != null) {
            patchDimension = applicationDescriptor.getPatchDimension();
        } else {
            patchDimension = null;
        }
        return patchDimension;
    }

    private static void zoomToPatchOnViewSelected(final Product product, final Patch patch, final Dimension patchDimension) {

        SnapApp.getDefault().getSelectionSupport(ProductSceneView.class).addHandler((oldValue, newValue) -> zoomToPatch(newValue, product, patch, patchDimension));
    }

    private static void zoomToPatch(ProductSceneView sceneView, Product product, Patch patch, Dimension patchDimension) {
        if (sceneView != null && sceneView.getProduct() == product) {
            Rectangle modelRect = new Rectangle(patch.getPatchX() * patchDimension.width,
                                                patch.getPatchY() * patchDimension.height,
                                                patchDimension.width, patchDimension.height);
            modelRect.grow(patchDimension.width / 2, patchDimension.height / 2);
            // System.out.println("modelRect = " + modelRect);
            sceneView.getLayerCanvas().setInitiallyZoomingAll(false);
            sceneView.zoom(modelRect);
        }
    }

    public static ProductSceneViewTopComponent findFrameForProductSceneView(Product product) {
        ProductSceneViewTopComponent selectedView = null;
        ProductSceneViewTopComponent multiBandView = null;
        ProductSceneViewTopComponent anyView = null;
        List<ProductSceneViewTopComponent> viewTopComponentList = WindowUtilities.getOpened(ProductSceneViewTopComponent.class).collect(Collectors.toList());

        for (ProductSceneViewTopComponent productSceneViewTopComponent : viewTopComponentList) {
            final ProductSceneView view = productSceneViewTopComponent.getView();

            if (view.getProduct() == product) {
                if (productSceneViewTopComponent.isSelected()) {
                    selectedView = productSceneViewTopComponent;
                }
                if (view.getNumRasters() > 1) {
                    multiBandView = productSceneViewTopComponent;
                }
                anyView = productSceneViewTopComponent;
            }
        }
        return selectedView != null ? selectedView : multiBandView != null ? multiBandView : anyView;
    }

}