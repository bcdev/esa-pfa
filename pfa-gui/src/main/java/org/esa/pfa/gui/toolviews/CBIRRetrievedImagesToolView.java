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
package org.esa.pfa.gui.toolviews;

import org.esa.pfa.classifier.Classifier;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.gui.ordering.ProductOrder;
import org.esa.pfa.gui.ordering.ProductOrderBasket;
import org.esa.pfa.gui.ordering.ProductOrderService;
import org.esa.pfa.gui.toolviews.support.DragScrollListener;
import org.esa.pfa.gui.toolviews.support.OptionsControlPanel;
import org.esa.pfa.gui.toolviews.support.PatchContextMenuFactory;
import org.esa.pfa.gui.toolviews.support.PatchDrawer;
import org.esa.pfa.gui.search.CBIRSession;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.SnapDialogs;
import org.esa.snap.rcp.util.ProgressHandleMonitor;
import org.esa.snap.rcp.windows.ToolTopComponent;
import org.netbeans.api.progress.ProgressUtils;
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
        mode = "editor",
        openAtStartup = false
)
@ActionID(category = "Window", id = "org.esa.pfa.gui.toolviews.CBIRRetrievedImagesToolView")
@ActionReferences({
        @ActionReference(path = "Menu/View/Tool Windows/PFA"),
        @ActionReference(path = "Toolbars/PFA", position = 4)
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

        drawer = new PatchDrawer(session);
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

            if (!hasClassifier) {
                topOptionsPanel.setInstructionTest(OptionsControlPanel.USE_CONTROL_CENTRE_INSTRUCTION);
            } else {
                topOptionsPanel.setInstructionTest("");
            }

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

                final ProgressHandleMonitor pm = ProgressHandleMonitor.create("Getting images to label");
                Runnable operation = () -> {
                    pm.beginTask("Getting images to label...", 100);
                    try {
                        session.getMostAmbigousPatches(false, pm);
                    } catch (Exception e) {
                        SnapApp.getDefault().handleError("Failed to get images", e);
                    } finally {
                        pm.done();
                    }
                };
                ProgressUtils.runOffEventThreadWithProgressDialog(operation, "Getting images to label", pm.getProgressHandle(), true, 50, 1000);
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
        updateControls();
    }

    @Override
    public void notifySessionMsg(final CBIRSession.Notification msg, final Classifier classifier) {
        switch (msg) {
            case NewClassifier:
                retrievedPatches = new Patch[0];
                drawer.update(retrievedPatches);
                updateControls();
                break;
            case DeleteClassifier:
                retrievedPatches = new Patch[0];
                drawer.update(retrievedPatches);
                updateControls();
                break;
            case NewTrainingImages:
                break;
            case NewQueryPatch:
                updateControls();
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

                    drawer.update(retrievedPatches);
                    updateControls();

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
                contextActions.add(new AbstractAction("Order All Relevant Parent Products") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        orderProducts(patchList, (String)getValue(NAME), true);
                    }
                });
                contextActions.add(new AbstractAction("Order All Parent Products") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        orderProducts(patchList, (String)getValue(NAME), false);
                    }
                });
            }
            return contextActions;
        }

        private void orderProducts(final List<Patch> patchList, final String name, final boolean onlyRelevant) {
            ProductOrderService productOrderService = CBIRSession.getInstance().getProductOrderService();
            ProductOrderBasket productOrderBasket = productOrderService.getProductOrderBasket();

            Set<String> productNameSet = new HashSet<>();
            for (Patch patch : patchList) {
                if(onlyRelevant && !patch.getLabel().equals(Patch.Label.RELEVANT)) {
                    continue;
                }
                String parentProductName = patch.getParentProductName();
                if (productOrderBasket.getProductOrder(parentProductName) == null) {
                    productNameSet.add(parentProductName);
                }
            }

            if (productNameSet.isEmpty()) {
                SnapDialogs.showInformation(name, "All parent data products have already been ordered.", null);
                return;
            }

            SnapDialogs.Answer resp = SnapDialogs.requestDecision(name,
                                                                  String.format("%d data product(s) will be ordered.\nProceed?",
                                                                                productNameSet.size()),
                                                                  true, null);
            if (resp == SnapDialogs.Answer.YES) {
                for (String productName : productNameSet) {
                    productOrderService.submit(new ProductOrder(productName));
                }
            }
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