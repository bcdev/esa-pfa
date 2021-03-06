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
import org.esa.pfa.gui.toolviews.support.DragScrollListener;
import org.esa.pfa.gui.toolviews.support.OptionsControlPanel;
import org.esa.pfa.gui.toolviews.support.PatchDrawer;
import org.esa.pfa.gui.search.CBIRSession;
import org.esa.snap.rcp.SnapApp;
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

@TopComponent.Description(
        preferredID = "CBIRLabelingToolView",
        iconBase = "images/icons/pfa-label-24.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS //todo define
)
@TopComponent.Registration(
        mode = "editor",
        openAtStartup = false
)
@ActionID(category = "Window", id = "org.esa.pfa.gui.toolviews.CBIRLabelingToolView")
@ActionReferences({
        @ActionReference(path = "Menu/View/Tool Windows/PFA"),
        @ActionReference(path = "Toolbars/PFA", position = 3)
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_CBIRLabelingToolView_Name",
        preferredID = "CBIRLabelingToolView"
)
@NbBundle.Messages({
        "CTL_CBIRLabelingToolView_Name=CBIR Labeling",
})
/**
 * Labeling Toolview
 */
public class CBIRLabelingToolView extends ToolTopComponent implements Patch.PatchListener, ActionListener,
        CBIRSession.Listener, OptionsControlPanel.Listener {

    private final static Dimension preferredDimension = new Dimension(550, 500);

    private final CBIRSession session;

    private PatchDrawer relavantDrawer;
    private PatchDrawer irrelavantDrawer;
    private JButton applyBtn;
    private JLabel iterationsLabel;
    private OptionsControlPanel topOptionsPanel;

    public CBIRLabelingToolView() {
        session = CBIRSession.getInstance();

        setLayout(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(4, 4, 4, 4));
        setDisplayName("PFA Labeling");
        add(createControl(), BorderLayout.CENTER);
        session.addListener(this);
    }

    public JComponent createControl() {

        final JPanel relevantPanel = new JPanel(new BorderLayout(2, 2));
        relevantPanel.setBorder(BorderFactory.createTitledBorder("Relevant Images"));

        relavantDrawer = new PatchDrawer(session);
        final JScrollPane relevantSP = new JScrollPane(relavantDrawer, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        final DragScrollListener relevantDSL = new DragScrollListener(relavantDrawer);
        relevantDSL.setDraggableElements(DragScrollListener.DRAGABLE_VERTICAL_SCROLL_BAR);
        relavantDrawer.addMouseListener(relevantDSL);
        relavantDrawer.addMouseMotionListener(relevantDSL);

        relevantPanel.add(relevantSP, BorderLayout.CENTER);

        final JPanel irrelevantPanel = new JPanel(new BorderLayout(2, 2));
        irrelevantPanel.setBorder(BorderFactory.createTitledBorder("Irrelevant Images"));

        irrelavantDrawer = new PatchDrawer(session);
        final JScrollPane irrelevantSP = new JScrollPane(irrelavantDrawer, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        final DragScrollListener irrelevantDSL = new DragScrollListener(irrelavantDrawer);
        relevantDSL.setDraggableElements(DragScrollListener.DRAGABLE_VERTICAL_SCROLL_BAR);
        irrelavantDrawer.addMouseListener(irrelevantDSL);
        irrelavantDrawer.addMouseMotionListener(irrelevantDSL);

        irrelevantPanel.add(irrelevantSP, BorderLayout.CENTER);

        JSplitPane drawerPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        drawerPane.setContinuousLayout(true);
        drawerPane.setResizeWeight(0.25);
        drawerPane.setTopComponent(relevantPanel);
        drawerPane.setBottomComponent(irrelevantPanel);

        final JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        iterationsLabel = new JLabel();
        bottomPanel.add(iterationsLabel);

        applyBtn = new JButton("Use Labels and Retrieve");
        applyBtn.setActionCommand("applyBtn");
        applyBtn.addActionListener(this);
        bottomPanel.add(applyBtn);

        topOptionsPanel = new OptionsControlPanel(session);
        topOptionsPanel.addListener(this);

        final JPanel mainPane = new JPanel(new BorderLayout(5, 5));
        mainPane.add(topOptionsPanel, BorderLayout.NORTH);
        mainPane.add(drawerPane, BorderLayout.CENTER);
        mainPane.add(bottomPanel, BorderLayout.SOUTH);

        updateControls();

        return mainPane;
    }

    private void updateControls() {
        try {
            boolean hasClassifier = session.hasClassifier();
            applyBtn.setEnabled(hasClassifier);

            if (hasClassifier) {
                final Patch[] relImages = session.getRelevantTrainingImages();
                final Patch[] irrelImages = session.getIrrelevantTrainingImages();
                relavantDrawer.update(relImages);
                irrelavantDrawer.update(irrelImages);
                int numIterations = session.getClassifierStats().getNumIterations();
                iterationsLabel.setText("Training iterations: " + numIterations);

                if (irrelImages.length > 0 || relImages.length > 0) {

                    topOptionsPanel.setInstructionTest("Click on an image to move it from the set of irrelevant images to relavant images");
                } else {
                    if(!session.hasQueryImages() && numIterations == 0) {
                        topOptionsPanel.setInstructionTest(OptionsControlPanel.USE_ADD_QUERY_INSTRUCTION);
                    } else {
                        topOptionsPanel.setInstructionTest("");
                    }
                }
            } else {
                final Patch[] noPatches = new Patch[0];
                relavantDrawer.update(noPatches);
                irrelavantDrawer.update(noPatches);
                iterationsLabel.setText("");
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
    @Override
    public void actionPerformed(final ActionEvent event) {
        final Window parentWindow = SwingUtilities.getWindowAncestor(this);
        try {
            final String command = event.getActionCommand();
            if (command.equals("applyBtn")) {
                if (!session.hasClassifier()) {
                    return;
                }

                // create window if needed first and add to session listeners
                CBIRControlCentreToolView.showWindow("CBIRRetrievedImagesToolView");

                final ProgressHandleMonitor pm = ProgressHandleMonitor.create("Retrieving");
                Runnable operation = () -> {
                    pm.beginTask("Retrieving images...", 100);
                    try {
                        session.trainAndClassify(false, pm);
                    } catch (Exception e) {
                        SnapApp.getDefault().handleError("Failed to retrieve images", e);
                    } finally {
                        pm.done();
                    }
                };
                ProgressUtils.runOffEventThreadWithProgressDialog(operation, "Retrieving images", pm.getProgressHandle(), true, 50, 1000);
            }
        } catch (Exception e) {
            SnapApp.getDefault().handleError("Error retrieving images", e);
        }
    }

    private void listenToPatches() {
        final Patch[] relPatches = session.getRelevantTrainingImages();
        for (Patch patch : relPatches) {
            patch.addListener(this);
        }
        final Patch[] irrelPatches = session.getIrrelevantTrainingImages();
        for (Patch patch : irrelPatches) {
            patch.addListener(this);
        }
    }

    @Override
    public void notifyStateChanged(final Patch patch) {
        session.reassignTrainingImage(patch);

        relavantDrawer.update(session.getRelevantTrainingImages());
        irrelavantDrawer.update(session.getIrrelevantTrainingImages());
    }

    @Override
    public void notifySessionMsg(final CBIRSession.Notification msg, final Classifier classifier) {
        switch (msg) {
            case NewClassifier:
                updateControls();
                break;
            case DeleteClassifier:
                updateControls();
                break;
            case NewTrainingImages:
                listenToPatches();
                updateControls();
                break;
            case NewQueryPatch:
                updateControls();
                break;
            case PatchDisplay:
                updateControls();
                break;
        }
    }

    @Override
    public void notifyOptionsMsg(final OptionsControlPanel.Notification msg) {
        switch (msg) {
            case QUICKLOOK_CHANGED:
                if (session.hasClassifier()) {
                    final Patch[] relPatches  = session.getRelevantTrainingImages();
                    final Patch[] irrelPatches = session.getIrrelevantTrainingImages();
                    relavantDrawer.update(relPatches);
                    irrelavantDrawer.update(irrelPatches);
                }
                break;
        }
    }
}