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
        position = 11
)
@ActionID(category = "Window", id = "org.esa.pfa.gui.toolviews.CBIRQueryToolView")
@ActionReferences({
        @ActionReference(path = "Menu/View/Tool Windows/PFA"),
        @ActionReference(path = "Toolbars/PFA", position = 2)
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

    private final CBIRSession session;
    private PatchDrawer drawer;
    private JButton editBtn, startTrainingBtn;
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
        final JScrollPane scrollPane = new JScrollPane(drawer, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                       JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        final DragScrollListener dl = new DragScrollListener(drawer);
        dl.setDraggableElements(DragScrollListener.DRAGABLE_VERTICAL_SCROLL_BAR);
        drawer.addMouseListener(dl);
        drawer.addMouseMotionListener(dl);

        imageScrollPanel.add(scrollPane);

        final JPanel listsPanel = new JPanel();
        final BoxLayout layout = new BoxLayout(listsPanel, BoxLayout.Y_AXIS);
        listsPanel.setLayout(layout);
        listsPanel.add(imageScrollPanel);

        mainPane.add(listsPanel, BorderLayout.CENTER);

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
                    final String[] bandNames = session.getApplicationDescriptor().getQuicklookFileNames();
                    final String defaultBandName = session.getApplicationDescriptor().getDefaultQuicklookFileName();
                    topOptionsPanel.populateQuicklookList(bandNames, defaultBandName);

                    topOptionsPanel.setInstructionTest("");
                } else {
                    topOptionsPanel.setInstructionTest(OptionsControlPanel.USE_ADD_QUERY_INSTRUCTION);
                }
            } else {
                topOptionsPanel.setInstructionTest(OptionsControlPanel.USE_CONTROL_CENTRE_INSTRUCTION);
            }
            topOptionsPanel.setEnabled(hasClassifier);
            startTrainingBtn.setEnabled(hasQueryImages);
            editBtn.setEnabled(false); //todo //hasQueryImages);
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
        try {
            final String command = event.getActionCommand();
            if (command.equals("startTrainingBtn")) {
                final Patch[] processedPatches = session.getQueryPatches();

                //only add patches with features
                final List<Patch> queryPatchList = new ArrayList<>(processedPatches.length);
                for (Patch patch : processedPatches) {
                    if (patch.getFeatureValues().length > 0 && patch.getLabel() == Patch.Label.RELEVANT) {
                        queryPatchList.add(patch);
                    }
                }
                if (queryPatchList.isEmpty()) {
                    throw new Exception("No features found in the relevant query images");
                }
                final Patch[] queryPatches = queryPatchList.toArray(new Patch[queryPatchList.size()]);

                // create window if needed first and add to session listeners
                CBIRControlCentreToolView.showWindow("CBIRLabelingToolView");

                ProgressHandleMonitor pm = ProgressHandleMonitor.create("Training");
                Runnable operation = () -> {
                    pm.beginTask("Training...", 100);
                    try {
                        session.startTraining(queryPatches, pm);
                    } catch (Exception e) {
                        SnapApp.getDefault().handleError("Failed to train classifier", e);
                    } finally {
                        pm.done();
                    }
                };

                ProgressUtils.runOffEventThreadWithProgressDialog(operation, "Training Classifier", pm.getProgressHandle(), true, 50, 1000);
            }
        } catch (Exception e) {
            SnapApp.getDefault().handleError("Error getting images", e);
        }
    }

    @Override
    public void notifySessionMsg(final CBIRSession.Notification msg, final Classifier classifier) {
        switch (msg) {
            case NewClassifier:
                topOptionsPanel.clearData();
                updateControls();

                drawer.update(session.getQueryPatches());
                break;
            case DeleteClassifier:
                topOptionsPanel.clearData();
                updateControls();

                drawer.update(new Patch[0]);
                break;
            case NewTrainingImages:
                break;
            case ModelTrained:
                updateControls();
                break;
            case NewQueryPatch:
                drawer.update(CBIRSession.getInstance().getQueryPatches());
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