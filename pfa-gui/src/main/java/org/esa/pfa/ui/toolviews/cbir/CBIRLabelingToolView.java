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

import com.bc.ceres.core.*;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.beam.framework.ui.application.support.AbstractToolView;
import org.esa.beam.visat.VisatApp;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.search.CBIRSession;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
    Labeling Toolview
 */
public class CBIRLabelingToolView extends AbstractToolView implements Patch.PatchListener, ActionListener,
        CBIRSession.CBIRSessionListener {

    public final static String ID = "org.esa.pfa.ui.toolviews.cbir.CBIRLabelingToolView";
    private final static Dimension preferredDimension = new Dimension(550, 500);

    private CBIRSession session;
    private PatchDrawer relavantDrawer;
    private PatchDrawer irrelavantDrawer;
    private JButton applyBtn;
    private JLabel iterationsLabel;

    public CBIRLabelingToolView() {
        CBIRSession.Instance().addListener(this);
    }

    public JComponent createControl() {

        final JPanel mainPane = new JPanel(new BorderLayout(5, 5));
        final JPanel relPanel = new JPanel(new BorderLayout(2, 2));
        relPanel.setBorder(BorderFactory.createTitledBorder("Relevant Images"));

        relavantDrawer = new PatchDrawer();
        final JScrollPane scrollPane1 = new JScrollPane(relavantDrawer, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                                                                        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        final DragScrollListener dl = new DragScrollListener(relavantDrawer);
        dl.setDraggableElements(DragScrollListener.DRAGABLE_HORIZONTAL_SCROLL_BAR);
        relavantDrawer.addMouseListener(dl);
        relavantDrawer.addMouseMotionListener(dl);

        relPanel.add(scrollPane1, BorderLayout.CENTER);

        final JPanel irrelPanel = new JPanel(new BorderLayout(2, 2));
        irrelPanel.setBorder(BorderFactory.createTitledBorder("Irrelevant Images"));

        irrelavantDrawer = new PatchDrawer();
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

        mainPane.add(listsPanel, BorderLayout.CENTER);

        final JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        iterationsLabel = new JLabel();
        bottomPanel.add(iterationsLabel);

        applyBtn = new JButton("Train and Apply Classifier");
        applyBtn.setActionCommand("applyBtn");
        applyBtn.addActionListener(this);
        bottomPanel.add(applyBtn);

        mainPane.add(bottomPanel, BorderLayout.SOUTH);

        updateControls();

        return mainPane;
    }

    @Override
    public void componentShown() {

        final Window win = getPaneWindow();
        if (win != null) {
            win.setPreferredSize(preferredDimension);
            win.setMaximumSize(preferredDimension);
            win.setSize(preferredDimension);
        }
    }

    private void updateControls() {
        applyBtn.setEnabled(session != null);

        if(session != null) {
            relavantDrawer.update(session.getRelevantTrainingImages());
            irrelavantDrawer.update(session.getIrrelevantTrainingImages());
            iterationsLabel.setText("Training iterations: "+session.getNumIterations());
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
            if(command.equals("applyBtn")) {
                getContext().getPage().showToolView(CBIRRetrievedImagesToolView.ID);

                final Window window = VisatApp.getApp().getApplicationWindow();
                ProgressMonitorSwingWorker<Boolean, Void> worker = new ProgressMonitorSwingWorker<Boolean, Void>(window, "Retrieving") {
                    @Override
                    protected Boolean doInBackground(final com.bc.ceres.core.ProgressMonitor pm) throws Exception {
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
            VisatApp.getApp().showErrorDialog(e.toString());
        }
    }

    private void listenToPatches() {
        final Patch[] relPatches = session.getRelevantTrainingImages();
        for(Patch patch : relPatches) {
            patch.addListener(this);
        }
        final Patch[] irrelPatches = session.getIrrelevantTrainingImages();
        for(Patch patch : irrelPatches) {
            patch.addListener(this);
        }
    }

    public void notifyNewSession() {
        session = CBIRSession.Instance();
    }

    public void notifyNewTrainingImages() {
        listenToPatches();

        if(isControlCreated()) {
            updateControls();
        }
    }

    public void notifyModelTrained() {
    }

    public void notifyStateChanged(final Patch patch) {
        session.reassignTrainingImage(patch);

        relavantDrawer.update(session.getRelevantTrainingImages());
        irrelavantDrawer.update(session.getIrrelevantTrainingImages());
    }
}