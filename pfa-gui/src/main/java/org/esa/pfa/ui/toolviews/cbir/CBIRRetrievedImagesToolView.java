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

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.pfa.classifier.ClassifierDelegate;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.ordering.ProductOrder;
import org.esa.pfa.ordering.ProductOrderBasket;
import org.esa.pfa.ordering.ProductOrderService;
import org.esa.pfa.search.CBIRSession;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.SnapDialogs;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@TopComponent.Description(
        preferredID = "CBIRRetrievedImagesToolView",
        iconBase = "images/icons/pfa-retrieve-24.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS //todo define
)
@TopComponent.Registration(
        mode = "navigator",
        openAtStartup = false,
        position = 1
)
@ActionID(category = "Window", id = "org.esa.pfa.ui.toolviews.cbir.CBIRRetrievedImagesToolView")
@ActionReferences({
        @ActionReference(path = "Menu/Window/Tool Windows"),
        @ActionReference(path = "Toolbars/PFA")
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_CBIRRetrievedImagesToolView_Name",
        preferredID = "CBIRRetrievedImagesToolView"
)
@NbBundle.Messages({
        "CTL_CBIRRetrievedImagesToolView_Name=CBIR Retrieved Images",
})
/**
 * Retrieved Images Panel
 */
public class CBIRRetrievedImagesToolView extends ToolTopComponent implements ActionListener,
        Patch.PatchListener, CBIRSession.Listener, OptionsControlPanel.Listener {

    private final CBIRSession session;
    private PatchDrawer drawer;
    private int accuracy = 0;
    private Patch[] retrievedPatches;
    private JButton improveBtn;
    private final JLabel accuracyLabel = new JLabel();
    private OptionsControlPanel topOptionsPanel;

    public CBIRRetrievedImagesToolView() {
        session = CBIRSession.getInstance();
        session.addListener(this);

        setLayout(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(4, 4, 4, 4));
        setDisplayName("CBIR Retrieved Images");
        add(createControl(), BorderLayout.CENTER);
    }

    public JComponent createControl() {

        final JPanel retPanel = new JPanel(new BorderLayout(2, 2));
        retPanel.setBorder(BorderFactory.createTitledBorder("Retrieved Images"));

        drawer = new PatchDrawer(session, true, new Patch[]{});
        drawer.setPatchContextMenuFactory(new RetrievedPatchContextMenuFactory());
        final JScrollPane scrollPane1 = new JScrollPane(drawer);

        final DragScrollListener dl = new DragScrollListener(drawer);
        drawer.addMouseListener(dl);
        drawer.addMouseMotionListener(dl);

        retPanel.add(scrollPane1, BorderLayout.CENTER);

        final JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(accuracyLabel);

        improveBtn = new JButton("Improve Classifier");
        improveBtn.setActionCommand("improveBtn");
        improveBtn.addActionListener(this);
        bottomPanel.add(improveBtn);

        topOptionsPanel = new OptionsControlPanel(session);
        topOptionsPanel.addListener(this);
        topOptionsPanel.showSetAllButtons(true);

        final JPanel mainPane = new JPanel(new BorderLayout(5, 5));
        mainPane.add(topOptionsPanel, BorderLayout.NORTH);
        mainPane.add(retPanel, BorderLayout.CENTER);
        mainPane.add(bottomPanel, BorderLayout.SOUTH);

        updateControls();

        return mainPane;
    }

    private void updateControls() {
        try {
            boolean hasClassifier = session.hasClassifier();
            topOptionsPanel.setEnabled(hasClassifier);

            final boolean haveRetrievedImages = hasClassifier && retrievedPatches != null && retrievedPatches.length > 0;
            improveBtn.setEnabled(haveRetrievedImages);
            topOptionsPanel.showSetAllButtons(haveRetrievedImages);

            if (haveRetrievedImages) {
                float pct = accuracy / (float) retrievedPatches.length * 100;
                accuracyLabel.setText("Accuracy: " + accuracy + '/' + retrievedPatches.length + " (" + (int) pct + "%)");

                topOptionsPanel.populateQuicklookList(session.getApplicationDescriptor().getQuicklookFileNames(),
                        session.getApplicationDescriptor().getDefaultQuicklookFileName());
            }
        } catch (Exception e) {
            SnapApp.getDefault().handleError("Error updating controls", e);
        }
    }

    /**
     * Handles events.
     *
     * @param event the event.
     */
    public void actionPerformed(final ActionEvent event) {
        final Window parentWindow = SwingUtilities.getWindowAncestor(this);
        try {
            final String command = event.getActionCommand();
            if (command.equals("improveBtn")) {

                CBIRControlCentreToolView.showWindow("CBIRLabelingToolView");

                ProgressMonitorSwingWorker<Boolean, Void> worker =
                        new ProgressMonitorSwingWorker<Boolean, Void>(parentWindow, "Getting images to label") {
                    @Override
                    protected Boolean doInBackground(ProgressMonitor pm) throws Exception {
                        pm.beginTask("Getting images...", 100);
                        try {
                            session.getMostAmbigousPatches(false, pm);
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
            }
        } catch (Exception e) {
            SnapApp.getDefault().handleError("Error getting images", e);
        }
    }

    private void listenToPatches() {
        for (Patch patch : retrievedPatches) {
            patch.addListener(this);
        }
    }

    @Override
    public void notifyStateChanged(final Patch notifyingPatch) {
        int cnt = 0;
        for (Patch patch : retrievedPatches) {
            if (patch.getLabel() == Patch.Label.RELEVANT) {
                cnt++;
            }
        }
        accuracy = cnt;
        if (isControlCreated()) {
            updateControls();
        }
    }

    //todo
    private boolean isControlCreated() {
        return true;
    }

    @Override
    public void notifySessionMsg(final CBIRSession.Notification msg, final ClassifierDelegate classifier) {
        switch (msg) {
            case NewClassifier:
                retrievedPatches = new Patch[0];
                if (isControlCreated()) {
                    drawer.update(retrievedPatches);
                    updateControls();
                }
                break;
            case DeleteClassifier:
                retrievedPatches = new Patch[0];
                if (isControlCreated()) {
                    drawer.update(retrievedPatches);
                    updateControls();
                }
                break;
            case NewTrainingImages:
                break;
            case ModelTrained:
                try {
                    retrievedPatches = session.getRetrievedImages();
                    //initially remove label from all
                    for (Patch patch : retrievedPatches) {
                        patch.setLabel(Patch.Label.NONE);
                    }
                    listenToPatches();

                    accuracy = 0;

                    if (isControlCreated()) {
                        drawer.update(retrievedPatches);
                        updateControls();
                    }

                } catch (Exception e) {
                    SnapApp.getDefault().handleError("Error training model", e);
                }
                break;
        }
    }

    private class RetrievedPatchContextMenuFactory extends PatchContextMenuFactory {

        private RetrievedPatchContextMenuFactory() {
            super(session);
        }

        @Override
        public List<Action> getContextActions(Patch patch) {
            List<Action> contextActions = super.getContextActions(patch);

            final List<Patch> patchList = drawer.getPatches();
            if (!patchList.isEmpty()) {
                contextActions.add(new AbstractAction("Order All Parent Products") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        ProductOrderService productOrderService = CBIRSession.getInstance().getProductOrderService();
                        ProductOrderBasket productOrderBasket = productOrderService.getProductOrderBasket();

                        Set<String> productNameSet = new HashSet<>();
                        for (Patch patch : patchList) {
                            String parentProductName = patch.getParentProductName();
                            if (productOrderBasket.getProductOrder(parentProductName) == null) {
                                productNameSet.add(parentProductName);
                            }
                        }

                        if (productNameSet.isEmpty()) {
                            SnapDialogs.showInformation((String) getValue(NAME),
                                        "All parent data products have already been ordered.", null);
                            return;
                        }

                        SnapDialogs.Answer resp = SnapDialogs.requestDecision((String) getValue(NAME),
                                                                              String.format("%d data product(s) will be ordered.\nProceed?",
                                                                                            productNameSet.size()),
                                                                              true, null);
                        if (resp == SnapDialogs.Answer.YES) {
                            for (String productName : productNameSet) {
                                productOrderService.submit(new ProductOrder(productName));
                            }
                        }
                    }
                });
            }
            return contextActions;
        }
    }

    @Override
    public void notifyOptionsMsg(final OptionsControlPanel.Notification msg) {
        switch (msg) {
            case SET_ALL_RELEVANT:
                for (Patch patch : retrievedPatches) {
                    patch.setLabel(Patch.Label.RELEVANT);
                }
                drawer.repaint();
                break;
            case SET_ALL_IRRELEVANT:
                for (Patch patch : retrievedPatches) {
                    patch.setLabel(Patch.Label.IRRELEVANT);
                }
                drawer.repaint();
                break;
            case QUICKLOOK_CHANGED:
                if (session.hasClassifier()) {
                    retrievedPatches = session.getRetrievedImages();
                    drawer.update(retrievedPatches);
                }
                break;
        }
    }
}