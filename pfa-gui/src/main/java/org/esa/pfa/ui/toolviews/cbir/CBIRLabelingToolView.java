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
import org.esa.pfa.search.CBIRSession;
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

@TopComponent.Description(
        preferredID = "CBIRLabelingToolView",
        iconBase = "images/icons/pfa-label-24.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS //todo define
)
@TopComponent.Registration(
        mode = "navigator",
        openAtStartup = false,
        position = 1
)
@ActionID(category = "Window", id = "org.esa.pfa.ui.toolviews.cbir.CBIRLabelingToolView")
@ActionReferences({
        @ActionReference(path = "Menu/Window/Tool Windows"),
        @ActionReference(path = "Toolbars/PFA")
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
        session.addListener(this);

        setLayout(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(4, 4, 4, 4));
        setDisplayName("CBIR Labeling");
        add(createControl(), BorderLayout.CENTER);
    }

    public JComponent createControl() {

        final JPanel relPanel = new JPanel(new BorderLayout(2, 2));
        relPanel.setBorder(BorderFactory.createTitledBorder("Relevant Images"));

        relavantDrawer = new PatchDrawer(session);
        final JScrollPane scrollPane1 = new JScrollPane(relavantDrawer, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                                                        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        final DragScrollListener dl = new DragScrollListener(relavantDrawer);
        dl.setDraggableElements(DragScrollListener.DRAGABLE_HORIZONTAL_SCROLL_BAR);
        relavantDrawer.addMouseListener(dl);
        relavantDrawer.addMouseMotionListener(dl);

        relPanel.add(scrollPane1, BorderLayout.CENTER);

        final JPanel irrelPanel = new JPanel(new BorderLayout(2, 2));
        irrelPanel.setBorder(BorderFactory.createTitledBorder("Irrelevant Images"));

        irrelavantDrawer = new PatchDrawer(session);
        final JScrollPane scrollPane2 = new JScrollPane(irrelavantDrawer, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                                                        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        final DragScrollListener dl2 = new DragScrollListener(irrelavantDrawer);
        dl.setDraggableElements(DragScrollListener.DRAGABLE_HORIZONTAL_SCROLL_BAR);
        irrelavantDrawer.addMouseListener(dl2);
        irrelavantDrawer.addMouseMotionListener(dl2);

        irrelPanel.add(scrollPane2, BorderLayout.CENTER);

        final JPanel listsPanel = new JPanel();
        final BoxLayout layout = new BoxLayout(listsPanel, BoxLayout.Y_AXIS);
        listsPanel.setLayout(layout);
        listsPanel.add(relPanel);
        listsPanel.add(irrelPanel);

        final JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        iterationsLabel = new JLabel();
        bottomPanel.add(iterationsLabel);

        applyBtn = new JButton("Train and Apply Classifier");
        applyBtn.setActionCommand("applyBtn");
        applyBtn.addActionListener(this);
        bottomPanel.add(applyBtn);

        topOptionsPanel = new OptionsControlPanel(session);
        topOptionsPanel.addListener(this);

        final JPanel mainPane = new JPanel(new BorderLayout(5, 5));
        mainPane.add(topOptionsPanel, BorderLayout.NORTH);
        mainPane.add(listsPanel, BorderLayout.CENTER);
        mainPane.add(bottomPanel, BorderLayout.SOUTH);

        updateControls();

        return mainPane;
    }

    public void componentShown() {

        final Window win = SwingUtilities.getWindowAncestor(this);
        if (win != null) {
            win.setPreferredSize(preferredDimension);
            win.setMaximumSize(preferredDimension);
            win.setSize(preferredDimension);
        }
    }

    private void updateControls() {
        try {
            boolean hasClassifier = session.hasClassifier();

            applyBtn.setEnabled(hasClassifier);
            topOptionsPanel.setEnabled(hasClassifier);

            if (hasClassifier) {
                final Patch[] relImages = session.getRelevantTrainingImages();
                final Patch[] irrelImages = session.getIrrelevantTrainingImages();
                relavantDrawer.update(relImages);
                irrelavantDrawer.update(irrelImages);
                iterationsLabel.setText("Training iterations: " + session.getNumIterations());

                if (irrelImages.length > 0 || relImages.length > 0) {
                    final String[] bandNames = session.getApplicationDescriptor().getQuicklookFileNames();
                    final String defaultBandName = session.getApplicationDescriptor().getDefaultQuicklookFileName();
                    topOptionsPanel.populateQuicklookList(bandNames, defaultBandName);
                }
            } else {
                Patch[] noPatches = new Patch[0];
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
                CBIRControlCentreToolView.showWindow(CBIRRetrievedImagesToolView.class, "CBIRRetrievedImagesToolView");

                ProgressMonitorSwingWorker<Boolean, Void> worker =
                        new ProgressMonitorSwingWorker<Boolean, Void>(parentWindow, "Retrieving") {
                    @Override
                    protected Boolean doInBackground(final ProgressMonitor pm) throws Exception {
                        pm.beginTask("Retrieving images...", 100);
                        try {
                            session.trainModel(pm);
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
    public void notifySessionMsg(final CBIRSession.Notification msg, final ClassifierDelegate classifier) {
        switch (msg) {
            case NewClassifier:
                if (isControlCreated()) {
                    updateControls();
                }
                break;
            case DeleteClassifier:
                if (isControlCreated()) {
                    updateControls();
                }
                break;
            case NewTrainingImages:
                listenToPatches();
                if (isControlCreated()) {
                    updateControls();
                }
                break;
            case ModelTrained:
                break;
        }
    }

    //todo
    private boolean isControlCreated() {
        return true;
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