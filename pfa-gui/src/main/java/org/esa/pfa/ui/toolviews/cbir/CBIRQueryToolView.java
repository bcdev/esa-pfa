/*
 * Copyright (C) 2013 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.pfa.ui.toolviews.cbir;

import com.bc.ceres.swing.figure.AbstractInteractorListener;
import com.bc.ceres.swing.figure.Interactor;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.actions.InsertFigureInteractorInterceptor;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.search.CBIRSession;
import org.esa.pfa.search.Classifier;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.windows.ToolTopComponent;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@TopComponent.Description(
        preferredID = "CBIRQueryToolView",
        iconBase = "images/icons/pfa-query-24.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS //todo define
)
@TopComponent.Registration(
        mode = "navigator",
        openAtStartup = false,
        position = 1
)
@ActionID(category = "Window", id = "org.esa.pfa.ui.toolviews.cbir.CBIRQueryToolView")
@ActionReferences({
        @ActionReference(path = "Menu/Window/Tool Windows"),
        @ActionReference(path = "Toolbars/PFA")
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_CBIRQueryToolView_Name",
        preferredID = "CBIRQueryToolView"
)
@NbBundle.Messages({
        "CTL_CBIRQueryToolView_Name=CBIR Query",
})
/**
 * Query Toolview
 */
public class CBIRQueryToolView extends ToolTopComponent implements ActionListener, CBIRSession.Listener,
        OptionsControlPanel.Listener {

    private final static Dimension preferredDimension = new Dimension(550, 300);

    private final CBIRSession session;
    private PatchDrawer drawer;
    private PatchSelectionInteractor interactor;
    private JButton addPatchBtn, editBtn, startTrainingBtn;
    private OptionsControlPanel topOptionsPanel;

    public CBIRQueryToolView() {
        session = CBIRSession.getInstance();
        session.addListener(this);

        setLayout(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(4, 4, 4, 4));
        setDisplayName("CBIR Query");
        add(createControl(), BorderLayout.CENTER);
    }

    public JComponent createControl() {

        final JPanel mainPane = new JPanel(new BorderLayout(5, 5));

        topOptionsPanel = new OptionsControlPanel(session);
        topOptionsPanel.addListener(this);

        mainPane.add(topOptionsPanel, BorderLayout.NORTH);

        final JPanel imageScrollPanel = new JPanel();
        imageScrollPanel.setLayout(new BoxLayout(imageScrollPanel, BoxLayout.X_AXIS));
        imageScrollPanel.setBorder(BorderFactory.createTitledBorder("Query Images"));

        drawer = new PatchDrawer(session);
        drawer.setMinimumSize(new Dimension(500, 310));
        final JScrollPane scrollPane = new JScrollPane(drawer, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                                                       JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        final DragScrollListener dl = new DragScrollListener(drawer);
        dl.setDraggableElements(DragScrollListener.DRAGABLE_HORIZONTAL_SCROLL_BAR);
        drawer.addMouseListener(dl);
        drawer.addMouseMotionListener(dl);

        imageScrollPanel.add(scrollPane);

        final JPanel listsPanel = new JPanel();
        final BoxLayout layout = new BoxLayout(listsPanel, BoxLayout.Y_AXIS);
        listsPanel.setLayout(layout);
        listsPanel.add(imageScrollPanel);

        mainPane.add(listsPanel, BorderLayout.CENTER);

        final JPanel btnPanel = new JPanel();
        addPatchBtn = new JButton("Add");
        addPatchBtn.setActionCommand("addPatchBtn");
        addPatchBtn.addActionListener(this);
        addPatchBtn.setEnabled(false);
        btnPanel.add(addPatchBtn);

        mainPane.add(btnPanel, BorderLayout.EAST);

        final JPanel bottomPanel = new JPanel();
        editBtn = new JButton("Edit Constraints");
        editBtn.setActionCommand("editBtn");
        editBtn.addActionListener(this);
        editBtn.setEnabled(false);
        bottomPanel.add(editBtn);

        startTrainingBtn = new JButton("Start Training");
        startTrainingBtn.setActionCommand("startTrainingBtn");
        startTrainingBtn.addActionListener(this);
        startTrainingBtn.setEnabled(false);
        bottomPanel.add(startTrainingBtn);

        mainPane.add(bottomPanel, BorderLayout.SOUTH);

        updateControls();

        return mainPane;
    }

    private void updateControls() {
        try {
            boolean hasClassifier = session.hasClassifier();
            boolean hasQueryImages = false;
            if (hasClassifier) {
                final Patch[] queryPatches = session.getQueryPatches();
                hasQueryImages = queryPatches.length > 0;

                if (hasQueryImages) {
                    final String[] bandNames = session.getAvailableQuickLooks(queryPatches[0]);
                    final String defaultBandName = session.getApplicationDescriptor().getDefaultQuicklookFileName();
                    topOptionsPanel.populateQuicklookList(bandNames, defaultBandName);
                }
            }
            topOptionsPanel.setEnabled(hasClassifier);
            addPatchBtn.setEnabled(hasClassifier);
            startTrainingBtn.setEnabled(hasQueryImages);
            editBtn.setEnabled(false); //todo //hasQueryImages);
        } catch (Exception e) {
            VisatApp.getApp().handleUnknownException(e);
        }
    }

    /**
     * Handles events.
     *
     * @param event the event.
     */
    public void actionPerformed(final ActionEvent event) {
        try {
            final String command = event.getActionCommand();
            if (command.equals("addPatchBtn")) {
                if (SnapApp.getDefault().getSelectedProductSceneView() == null) {
                    throw new Exception("First open a product and an image view to be able to add new query images.");
                }

                final Dimension dim = session.getApplicationDescriptor().getPatchDimension();
                interactor = new PatchSelectionInteractor(dim.width, dim.height);
                interactor.addListener(new PatchInteractorListener());
                interactor.addListener(new InsertFigureInteractorInterceptor());
                interactor.activate();

                VisatApp.getApp().setActiveInteractor(interactor);
            } else if (command.equals("startTrainingBtn")) {
                final Patch[] processedPatches = session.getQueryPatches();

                //only add patches with features
                final List<Patch> queryPatches = new ArrayList<>(processedPatches.length);
                for (Patch patch : processedPatches) {
                    if (patch.getFeatures().length > 0 && patch.getLabel() == Patch.LABEL_RELEVANT) {
                        queryPatches.add(patch);
                    }
                }
                if (queryPatches.isEmpty()) {
                    throw new Exception("No features found in the relevant query images");
                }
                final Patch[] queryImages = queryPatches.toArray(new Patch[queryPatches.size()]);

            /*    ProgressMonitorSwingWorker<Boolean, Void> worker = new ProgressMonitorSwingWorker<Boolean, Void>(getControl(), "Getting images to label") {
                    @Override
                    protected Boolean doInBackground(final ProgressMonitor pm) throws Exception {
                        pm.beginTask("Getting images...", 100);
                        try {
                            session.setQueryImages(queryImages, pm);
                            if (!pm.isCanceled()) {
                                return Boolean.TRUE;
                            }
                        } finally {
                            pm.done();
                        }
                        return Boolean.FALSE;
                    }
                };
                worker.executeWithBlocking();
                if (worker.get()) {
                    getContext().getPage().showToolView(CBIRLabelingToolView.ID);
                }*/
            }
        } catch (Exception e) {
            SnapApp.getDefault().handleError("Error getting images", e);
        }
    }

    private class PatchInteractorListener extends AbstractInteractorListener {

        @Override
        public void interactionStarted(Interactor interactor, InputEvent inputEvent) {
        }

        @Override
        public void interactionStopped(Interactor interactor, InputEvent inputEvent) {
            if (!session.hasClassifier()) {
                return;
            }
            final PatchSelectionInteractor patchInteractor = (PatchSelectionInteractor) interactor;
            if (patchInteractor != null) {
                try {
                    Rectangle2D rect = patchInteractor.getPatchShape();

                    ProductSceneView productSceneView = getProductSceneView(inputEvent);
                    RenderedImage parentImage = productSceneView != null ? productSceneView.getBaseImageLayer().getImage() : null;

                    final Product product = SnapApp.getDefault().getSelectedProduct();
                    addQueryImage(product, (int) rect.getX(), (int) rect.getY(), (int) rect.getWidth(), (int) rect.getHeight(), parentImage);


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void addQueryImage(final Product product, final int x, final int y, final int w, final int h,
                                   final RenderedImage parentImage) throws IOException {

            final Rectangle region = new Rectangle(x, y, w, h);
         /*  //todo final PatchProcessor patchProcessor = new PatchProcessor(getControl(), product, parentImage, region, session);
           //todo patchProcessor.executeWithBlocking();
            Patch patch = null;
            try {
                //todo patch = patchProcessor.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                VisatApp.getApp().handleError("Failed to extract patch", e);
            }
            if (patch != null && patch.getFeatures().length > 0) {
                session.addQueryPatch(patch);
                drawer.update(session.getQueryPatches());
                updateControls();
            } else {
                VisatApp.getApp().showWarningDialog("Failed to extract features for this patch");
            }*/
        }

        private ProductSceneView getProductSceneView(InputEvent event) {
            ProductSceneView productSceneView = null;
            Component component = event.getComponent();
            while (component != null) {
                if (component instanceof ProductSceneView) {
                    productSceneView = (ProductSceneView) component;
                    break;
                }
                component = component.getParent();
            }
            return productSceneView;
        }
    }

    //todo @Override
    public void componentShown() {

        final Window win = SwingUtilities.getWindowAncestor(this);
        if (win != null) {
            win.setPreferredSize(preferredDimension);
            win.setMaximumSize(preferredDimension);
            win.setSize(preferredDimension);
        }
    }

    @Override
    public void notifySessionMsg(final CBIRSession.Notification msg, final Classifier classifier) {
        switch (msg) {
            case NewClassifier:
                /*if (isControlCreated()) {
                    topOptionsPanel.clearData();
                    updateControls();

                    drawer.update(session.getQueryPatches());
                }*/
                break;
            case DeleteClassifier:
              /*  if (isControlCreated()) {
                    topOptionsPanel.clearData();
                    updateControls();

                    drawer.update(new Patch[0]);
                }*/
                break;
            case NewTrainingImages:
                break;
            case ModelTrained:
                updateControls();
                break;
        }
    }

    @Override
    public void notifyOptionsMsg(final OptionsControlPanel.Notification msg) {
        switch (msg) {
            case QUICKLOOK_CHANGED:
                if (session.hasClassifier()) {
                    drawer.update(session.getQueryPatches());
                }
                break;
        }
    }
}