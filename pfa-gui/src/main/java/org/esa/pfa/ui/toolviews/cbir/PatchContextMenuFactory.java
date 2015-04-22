package org.esa.pfa.ui.toolviews.cbir;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.snap.framework.dataio.ProductIO;
import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.ui.ModelessDialog;
import org.esa.snap.framework.ui.product.ProductSceneView;
import org.esa.snap.util.Debug;
import org.esa.snap.util.ProductUtils;
import org.esa.snap.visat.VisatApp;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.ordering.ProductOrder;
import org.esa.pfa.ordering.ProductOrderBasket;
import org.esa.pfa.ordering.ProductOrderService;
import org.esa.pfa.search.CBIRSession;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.SnapDialogs;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @author Norman Fomferra
 */
public class PatchContextMenuFactory {

    private final CBIRSession session;

    public PatchContextMenuFactory(CBIRSession session) {
        this.session = session;
    }

    protected CBIRSession getSession() {
        return session;
    }

    public JPopupMenu createContextMenu(final Patch patch) {
        List<Action> actionList = getContextActions(patch);
        if (actionList.isEmpty()) {
            return null;
        }
        JPopupMenu popupMenu = new JPopupMenu();
        for (Action action : actionList) {
            popupMenu.add(action);
        }
        return popupMenu;
    }

    public List<Action> getContextActions(final Patch patch) {
        List<Action> actionList = new ArrayList<>();

        Action showPatchInfoAction = createShowPatchInfoAction(patch);
        if (showPatchInfoAction != null) {
            actionList.add(showPatchInfoAction);
        }

        Action openPatchProductAction = createOpenPatchProductAction(patch);
        if (showPatchInfoAction != null) {
            actionList.add(openPatchProductAction);
        }

        Action openParentProductAction = createShowPatchInParentProductAction(patch);
        if (openParentProductAction != null) {
            actionList.add(openParentProductAction);
        }

        Action orderParentProductAction = createOrderParentProductAction(patch);
        if (orderParentProductAction != null) {
            actionList.add(orderParentProductAction);
        }

        return actionList;
    }

    public Action createOrderParentProductAction(final Patch patch) {
        final String parentProductName = patch.getParentProductName();
        if (parentProductName == null) {
            return null;
        }

        return new AbstractAction("Order Parent Product") {

            @Override
            public void actionPerformed(ActionEvent e) {
                orderParentProduct(patch);
            }

            private void orderParentProduct(Patch patch) {
                ProductOrderBasket productOrderBasket = CBIRSession.getInstance().getProductOrderBasket();
                ProductOrder productOrder = productOrderBasket.getProductOrder(parentProductName);
                if (productOrder != null) {
                    if (productOrder.getState() == ProductOrder.State.COMPLETED) {
                        SnapDialogs.Answer resp = SnapDialogs.requestDecision((String) getValue(NAME),
                                                               String.format("Data product\n%s\nhas already been downloaded.\nOpen it?",
                                                                             parentProductName), true, null);
                        if (resp == SnapDialogs.Answer.YES) {
                            createShowPatchInParentProductAction(patch);
                        }
                    } else {
                        SnapDialogs.showInformation((String) getValue(NAME),
                                                    String.format("Data product\n%s\nis already in the basket.",
                                                                  parentProductName), null);
                    }
                    return;
                }

                SnapDialogs.Answer resp = SnapDialogs.requestDecision((String) getValue(NAME),
                                                                      String.format("Data product\n%s\nwill be ordered.\nProceed?",
                                                                                    parentProductName), true, null);
                if (resp == SnapDialogs.Answer.YES) {
                    ProductOrderService productOrderService = CBIRSession.getInstance().getProductOrderService();
                    productOrderService.submit(new ProductOrder(parentProductName));
                }
            }
        };
    }


    public Action createShowPatchInParentProductAction(final Patch patch) {

        String parentProductName = patch.getParentProductName();
        if (parentProductName == null) {
            return null;
        }

        // O-oh, no good design here...
        CBIRSession session = CBIRSession.getInstance();
        PFAApplicationDescriptor applicationDescriptor = session.getApplicationDescriptor();
        if (applicationDescriptor == null) {
            // session not init?
            return null;
        }

        File localProductDir = applicationDescriptor.getLocalProductDir();
        if (localProductDir == null) {
            // config property not set?
            return null;
        }

        final File parentProductFile = new File(localProductDir, parentProductName);
        if (!parentProductFile.exists()) {
            return null;
        }

        return new AbstractAction("Show Patch in Parent Product") {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    showPatchInParentProduct(patch, parentProductFile);
                } catch (Exception ioe) {
                    Debug.trace(ioe);
                    SnapApp.getDefault().handleError("Failed to open parent product.", ioe);
                }
            }
        };
    }

    public Action createOpenPatchProductAction(final Patch patch) {
        File patchProductFile = null;
        if (getSession().hasClassifier()) {
            try {
                patchProductFile = getSession().getClassifier().getPatchProductFile(patch);
            } catch (IOException ignore) {
                Debug.trace(ignore);
            }
        }

        if (patchProductFile != null && !patchProductFile.exists()) {
            return null;
        }

        final File finalPatchProductFile = patchProductFile;
        return new AbstractAction("Open Patch Product") {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    showPatchInParentProduct(patch, finalPatchProductFile);
                } catch (Exception ioe) {
                    Debug.trace(ioe);
                    SnapApp.getDefault().handleError("Failed to open patch product.", ioe);
                }
            }
        };
    }

    public Action createShowPatchInfoAction(final Patch patch) {
        if (patch.getFeatures().length == 0) {
            return null;
        }

        return new AbstractAction("Show Patch Info") {
            @Override
            public void actionPerformed(ActionEvent e) {
                showPatchInfo(patch);
            }

            private void showPatchInfo(Patch patch) {
                PatchInfoDialog patchInfoDialog = new PatchInfoDialog(null, patch, createOtherButtons(patch));
                patchInfoDialog.show();
            }

            private JButton[] createOtherButtons(Patch patch) {
                Action openParentProductAction = createShowPatchInParentProductAction(patch);
                if (openParentProductAction != null) {
                    JButton button = new JButton(openParentProductAction);
                    return new JButton[]{button};
                }
                return null;
            }
        };

    }


    private static void showPatchInParentProduct(final Patch patch, File patchProductFile) throws Exception {
        final Product product = openProduct(patchProductFile);
        if (product == null) {
            return;
        }
        JInternalFrame frame = findFrameForProductSceneView(product);
        // First check if we can reuse the currently selected view
        if (frame != null) {
            frame.setSelected(true);
            ProductSceneView sceneView = (ProductSceneView) frame.getContentPane();
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
            VisatApp.getApp().openProductSceneView(band);
        }
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
        VisatApp visatApp = VisatApp.getApp();
        final InternalFrameAdapter sceneViewTracker = new InternalFrameAdapter() {

            @Override
            public void internalFrameActivated(InternalFrameEvent e) {
                JInternalFrame internalFrame = e.getInternalFrame();
                ProductSceneView sceneView = getProductSceneView(internalFrame);
                if (sceneView != null) {
                    zoomToPatch(sceneView, product, patch, patchDimension);
                    VisatApp.getApp().removeInternalFrameListener(this);
                }
            }

        };
        visatApp.addInternalFrameListener(sceneViewTracker);
        Timer timer = new Timer(5000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                VisatApp.getApp().removeInternalFrameListener(sceneViewTracker);
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private static void zoomToPatch(ProductSceneView sceneView, Product product, Patch patch, Dimension patchDimension) {
        if (sceneView != null && sceneView.getProduct() == product) {
            Rectangle modelRect = new Rectangle(patch.getPatchX() * patchDimension.width,
                                                patch.getPatchY() * patchDimension.height,
                                                patchDimension.width, patchDimension.height);
            modelRect.grow(patchDimension.width / 2, patchDimension.height / 2);
//            System.out.println("modelRect = " + modelRect);
            sceneView.getLayerCanvas().setInitiallyZoomingAll(false);
            sceneView.zoom(modelRect);
        }
    }

    private static ProductSceneView getProductSceneView(JInternalFrame internalFrame) {
        return internalFrame != null && internalFrame.getContentPane() instanceof ProductSceneView ? (ProductSceneView) internalFrame.getContentPane() : null;
    }

    public static Product openProduct(final File productFile) throws Exception {
        final VisatApp visat = VisatApp.getApp();
        Product product = visat.getOpenProduct(productFile);
        if (product != null) {
            return product;
        }
        ProgressMonitorSwingWorker<Product, Void> worker = new ProgressMonitorSwingWorker<Product, Void>(VisatApp.getApp().getApplicationWindow(), "Navigate to patch") {
            @Override
            protected Product doInBackground(ProgressMonitor progressMonitor) throws Exception {
                return ProductIO.readProduct(productFile);
            }

            @Override
            protected void done() {
                try {
                    visat.getProductManager().addProduct(get());
                } catch (InterruptedException | ExecutionException e) {
                    Debug.trace(e);
                    VisatApp.getApp().handleError("Failed to open product.", e);
                }
            }
        };
        worker.executeWithBlocking();
        return worker.get();
    }


    private static class PatchInfoDialog extends ModelessDialog {

        public PatchInfoDialog(Window parent, Patch patch, JButton[] buttons) {
            super(parent, "Patch Info - " + patch.getPatchName(), ID_CLOSE, buttons, null);

            Object[][] array = getFeatureTableData(patch);
            JTable table = new JTable(new DefaultTableModel(array, new Object[]{"Name", "Value"}));

            JPanel contentPanel = new JPanel(new BorderLayout(2, 2));
            final String ql = CBIRSession.getInstance().getQuicklookBandName1();
            if (patch.getImage(ql) != null) {

                JLabel imageCanvas = new JLabel(new ImageIcon(patch.getImage(ql)));
                imageCanvas.setBorder(new LineBorder(Color.DARK_GRAY));

                JPanel compRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
                compRow.add(imageCanvas);
                compRow.setPreferredSize(imageCanvas.getPreferredSize());

                contentPanel.add(compRow, BorderLayout.NORTH);
            }
            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setPreferredSize(new Dimension(200, 80));
            contentPanel.add(scrollPane, BorderLayout.CENTER);
            setContent(contentPanel);
        }

        private Object[][] getFeatureTableData(Patch patch) {
            ArrayList<Object[]> data = new ArrayList<>();
            for (Feature feature : patch.getFeatures()) {
                data.add(new Object[]{feature.getName(), feature.getValue()});
            }
            return data.toArray(new Object[0][]);
        }
    }


    public static JInternalFrame findFrameForProductSceneView(Product product) {
        final JInternalFrame[] frames = VisatApp.getApp().getAllInternalFrames();
        JInternalFrame selectedView = null;
        JInternalFrame multiBandView = null;
        JInternalFrame anyView = null;
        for (final JInternalFrame frame : frames) {
            final Container contentPane = frame.getContentPane();
            if (contentPane instanceof ProductSceneView) {
                final ProductSceneView view = (ProductSceneView) contentPane;
                if (view.getProduct() == product) {
                    if (frame.isSelected()) {
                        selectedView = frame;
                    }
                    if (view.getNumRasters() > 1) {
                        multiBandView = frame;
                    }
                    anyView = frame;
                }
            }
        }
        return selectedView != null ? selectedView : multiBandView != null ? multiBandView : anyView;
    }


}
