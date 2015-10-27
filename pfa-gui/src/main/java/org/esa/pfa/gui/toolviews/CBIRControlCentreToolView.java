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


import com.bc.ceres.core.SubProgressMonitor;
import com.jidesoft.swing.FolderChooser;
import org.esa.pfa.classifier.Classifier;
import org.esa.pfa.classifier.ClassifierStats;
import org.esa.pfa.classifier.DatabaseManager;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.gui.search.CBIRSession;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.SnapDialogs;
import org.esa.snap.rcp.util.ProgressHandleMonitor;
import org.esa.snap.rcp.windows.ToolTopComponent;
import org.esa.snap.ui.GridBagUtils;
import org.esa.snap.ui.ModalDialog;
import org.netbeans.api.progress.ProgressUtils;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.util.prefs.Preferences;

@TopComponent.Description(
        preferredID = "CBIRControlCentreToolView",
        iconBase = "images/icons/pfa-control-24.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS //todo define
)
@TopComponent.Registration(
        mode = "navigator",
        openAtStartup = false,
        position = 10
)
@ActionID(category = "Window", id = "org.esa.pfa.gui.toolviews.CBIRControlCentreToolView")
@ActionReferences({
        @ActionReference(path = "Menu/View/Tool Windows/PFA"),
        @ActionReference(path = "Toolbars/PFA", position = 1)
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_CBIRControlCentreToolView_Name",
        preferredID = "CBIRControlCentreToolView"
)
@NbBundle.Messages({
        "CTL_CBIRControlCentreToolView_Name=CBIR Control Centre",
})
/**
 * Control Centre Toolview
 */
public class CBIRControlCentreToolView extends ToolTopComponent implements CBIRSession.Listener {

    private JList<String> classifierList;
    private JButton newBtn, deleteBtn;
    private JButton queryBtn, labelBtn, applyBtn;
    private JTextField numTrainingImages;
    private JTextField numRetrievedImages;
    private JButton updateBtn;
    private JLabel iterationsLabel = new JLabel();
    private JLabel patchesInTrainingLabel = new JLabel();
    private JLabel patchesInQueryLabel = new JLabel();
    private JLabel patchesInTestLabel = new JLabel();
    private JLabel applicationLabel;

    private final CBIRSession session;

    public CBIRControlCentreToolView() {
        session = CBIRSession.getInstance();
        session.addListener(this);

        setLayout(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(4, 4, 4, 4));
        setDisplayName("CBIR Control");
        add(createControl(), BorderLayout.CENTER);
    }

    public JComponent createControl() {

        final JPanel contentPane = new JPanel(new GridBagLayout());

        applicationLabel = new JLabel("");

        final JPanel applicationPanel = new JPanel(new BorderLayout(4, 4));
        applicationPanel.add(applicationLabel, BorderLayout.NORTH);

        final GridBagConstraints gbc = GridBagUtils.createDefaultConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;

        contentPane.add(applicationPanel, gbc);
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridwidth = 1;
        contentPane.add(new JLabel("Saved Classifiers:"), gbc);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy++;

        classifierList = new JList<>();
        classifierList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        classifierList.setLayoutOrientation(JList.VERTICAL);
        classifierList.setPrototypeCellValue("12345678901234567890");
        classifierList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                try {
                    if (!e.getValueIsAdjusting()) {
                        String classifierName = classifierList.getSelectedValue();
                        if (classifierName != null) {
                            Classifier classifier = session.getClassifier();
                            if (classifier == null || !classifierName.equals(classifier.getName())) {
                                session.loadClassifier(classifierName);
                            }
                        }
                    }
                } catch (Throwable t) {
                    SnapApp.getDefault().handleError("Error loading classifier", t);
                }
            }
        });
        contentPane.add(new JScrollPane(classifierList), gbc);

        final JPanel optionsPane = new JPanel(new GridBagLayout());
        final GridBagConstraints gbcOpt = GridBagUtils.createDefaultConstraints();
        gbcOpt.fill = GridBagConstraints.HORIZONTAL;
        gbcOpt.anchor = GridBagConstraints.NORTHWEST;
        gbcOpt.gridx = 0;
        gbcOpt.gridy = 0;

        optionsPane.add(new JLabel("# of training images:"), gbcOpt);
        gbcOpt.gridx = 1;
        gbcOpt.weightx = 0.8;
        numTrainingImages = new JTextField();
        numTrainingImages.setColumns(3);
        numTrainingImages.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (session.hasClassifier()) {
                        session.setNumTrainingImages(Integer.parseInt(numTrainingImages.getText()));
                    }
                } catch (Throwable t) {
                    SnapApp.getDefault().handleError("Error setting number of training images", t);
                }
            }
        });
        optionsPane.add(numTrainingImages, gbcOpt);

        gbcOpt.weightx = 1;
        gbcOpt.gridy++;
        gbcOpt.gridx = 0;
        optionsPane.add(new JLabel("# of retrieved images:"), gbcOpt);
        gbcOpt.gridx = 1;
        numRetrievedImages = new JTextField();
        numRetrievedImages.setColumns(3);
        numRetrievedImages.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (session.hasClassifier()) {
                        session.setNumRetrievedImages(Integer.parseInt(numRetrievedImages.getText()));
                    }
                } catch (Throwable t) {
                    SnapApp.getDefault().handleError("Error setting retrieved images", t);
                }
            }
        });
        optionsPane.add(numRetrievedImages, gbcOpt);

        gbcOpt.weightx = 1;
        gbcOpt.gridy++;
        gbcOpt.gridx = 0;
        optionsPane.add(new JLabel("# of iterations:"), gbcOpt);
        gbcOpt.gridx = 1;
        optionsPane.add(iterationsLabel, gbcOpt);
        gbcOpt.gridy++;
        gbcOpt.gridx = 0;
        optionsPane.add(new JLabel("# of training patches:"), gbcOpt);
        gbcOpt.gridx = 1;
        optionsPane.add(patchesInTrainingLabel, gbcOpt);
        gbcOpt.gridy++;
        gbcOpt.gridx = 0;
        optionsPane.add(new JLabel("# of query patches:"), gbcOpt);
        gbcOpt.gridx = 1;
        optionsPane.add(patchesInQueryLabel, gbcOpt);
        gbcOpt.gridy++;
        gbcOpt.gridx = 0;
        optionsPane.add(new JLabel("# of test pacthes:"), gbcOpt);
        gbcOpt.gridx = 1;
        optionsPane.add(patchesInTestLabel, gbcOpt);

        updateBtn = new JButton(new AbstractAction("Update") {
            public void actionPerformed(ActionEvent e) {
                try {
                    if (session.hasClassifier()) {
                        session.setNumTrainingImages(Integer.parseInt(numTrainingImages.getText()));
                        session.setNumRetrievedImages(Integer.parseInt(numRetrievedImages.getText()));
                    }
                } catch (Throwable t) {
                    SnapApp.getDefault().handleError("Error updating retrieved images", t);
                }
            }
        });
        gbcOpt.gridy++;
        gbcOpt.gridx = 1;
        optionsPane.add(updateBtn, gbcOpt);

        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 1;
        contentPane.add(optionsPane, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        contentPane.add(createClassifierButtonPanel(), gbc);

        final JPanel mainPane0 = new JPanel(new BorderLayout());
        mainPane0.add(contentPane, BorderLayout.CENTER);
        mainPane0.add(createSideButtonPanel(), BorderLayout.EAST);

        final JPanel mainPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        mainPane.add(mainPane0);

        initClassifierList();
        updateControls();

        return mainPane;
    }

    private void initClassifierList() {
        final DefaultListModel<String> modelList = new DefaultListModel<>();
        for (String name : session.listClassifiers()) {
            if(!name.isEmpty()) {
                modelList.addElement(name);
            }
        }
        classifierList.setModel(modelList);
        updateControls();
    }

    private JPanel createClassifierButtonPanel() {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        newBtn = new JButton(new AbstractAction("New") {
            public void actionPerformed(ActionEvent e) {
                try {
                    final NewClassifierDialog dlg = new NewClassifierDialog("New Classifier", "Name:", "");
                    dlg.show();

                    String classifierName = dlg.getClassifierName();
                    if (!classifierName.isEmpty()) {
                        createNewClassifier(classifierName, session);
                    }
                } catch (Throwable t) {
                    SnapApp.getDefault().handleError("Error creating new classifier", t);
                }
            }
        });
        deleteBtn = new JButton(new AbstractAction("Delete") {
            public void actionPerformed(ActionEvent e) {
                try {
                    session.deleteClassifier();
                } catch (Throwable t) {
                    SnapApp.getDefault().handleError("Error deleting classifier", t);
                }
            }
        });

        panel.add(newBtn);
        panel.add(deleteBtn);

        return panel;
    }

    public static void showWindow(final String windowID) {

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                final TopComponent window = WindowManager.getDefault().findTopComponent(windowID);
                window.open();
                window.requestActive();
            }
        });
    }

    private JPanel createSideButtonPanel() {
        final JPanel panel = new JPanel(new GridLayout(-1, 1, 2, 2));

        queryBtn = new JButton(new AbstractAction("Query") {
            public void actionPerformed(ActionEvent e) {
                try {
                    showWindow("CBIRQueryToolView");
                } catch (Throwable t) {
                    SnapApp.getDefault().handleError("Error calling Query", t);
                }
            }
        });
        labelBtn = new JButton(new AbstractAction("Labeling") {
            public void actionPerformed(ActionEvent e) {
                if (!session.hasClassifier()) {
                    return;
                }
                try {
                    final ProgressHandleMonitor pm = ProgressHandleMonitor.create("Getting images to label");
                    Runnable operation = () -> {
                        pm.beginTask("Getting images to label...", 100);
                        try {
                            session.getMostAmbigousPatches(true, pm);
                        } catch (Exception ex) {
                            SnapApp.getDefault().handleError("Failed to get images", ex);
                        } finally {
                            pm.done();
                        }
                    };
                    ProgressUtils.runOffEventThreadWithProgressDialog(operation, "Getting images to label", pm.getProgressHandle(), true, 50, 1000);

                    showWindow("CBIRLabelingToolView");

                } catch (Throwable t) {
                    SnapApp.getDefault().handleError("Error getting images", t);
                }
            }
        });
        applyBtn = new JButton(new AbstractAction("Retrieve") {
            public void actionPerformed(ActionEvent e) {
                try {
                    if (!session.hasClassifier()) {
                        return;
                    }
                    final ProgressHandleMonitor pm = ProgressHandleMonitor.create("Retrieving");
                    Runnable operation = () -> {
                        pm.beginTask("Retrieving images...", 100);
                        try {
                            session.trainAndClassify(true, SubProgressMonitor.create(pm, 50));
                        } catch (Exception ex) {
                            SnapApp.getDefault().handleError("Failed to retrieve images", ex);
                        } finally {
                            pm.done();
                        }
                    };
                    ProgressUtils.runOffEventThreadWithProgressDialog(operation, "Retrieving images", pm.getProgressHandle(), true, 50, 1000);

                    showWindow("CBIRRetrievedImagesToolView");

                } catch (Throwable t) {
                    SnapApp.getDefault().handleError("Error retrieving images", t);
                }
            }
        });

        panel.add(queryBtn);
        panel.add(labelBtn);
        panel.add(applyBtn);

        final JPanel panel2 = new JPanel(new BorderLayout(2, 2));
        panel2.add(panel, BorderLayout.NORTH);
        panel2.add(new JLabel(new ImageIcon(getClass().getResource("/images/pfa-logo-small.png"))), BorderLayout.SOUTH);
        return panel2;
    }

    private void updateControls() {
        PFAApplicationDescriptor appDescriptor = session.getApplicationDescriptor();
        if (appDescriptor != null) {
            applicationLabel.setText("Application: " + appDescriptor.getName());
        } else {
            applicationLabel.setText("");
        }
        newBtn.setEnabled(session.hasClassifierManager());

        final boolean hasActiveClassifier = session.hasClassifier();
        deleteBtn.setEnabled(hasActiveClassifier);

        numTrainingImages.setEnabled(hasActiveClassifier);
        numRetrievedImages.setEnabled(hasActiveClassifier);
        updateBtn.setEnabled(hasActiveClassifier);

        queryBtn.setEnabled(hasActiveClassifier);
        labelBtn.setEnabled(hasActiveClassifier);
        applyBtn.setEnabled(hasActiveClassifier);

        if (hasActiveClassifier) {
            ClassifierStats classifierStats = session.getClassifierStats();

            int numIterations = classifierStats.getNumIterations();
            numTrainingImages.setText(String.valueOf(classifierStats.getNumTrainingImages()));
            numRetrievedImages.setText(String.valueOf(classifierStats.getNumRetrievedImages()));
            iterationsLabel.setText(String.valueOf(numIterations));
            patchesInQueryLabel.setText(String.valueOf(classifierStats.getNumPatchesInQueryData()));
            patchesInTestLabel.setText(String.valueOf(classifierStats.getNumPatchesInTestData()));
            patchesInTrainingLabel.setText(String.valueOf(classifierStats.getNumPatchesInTrainingData()));

            labelBtn.setEnabled(numIterations > 0);
            applyBtn.setEnabled(numIterations > 0);
        }
    }


    private static class NewClassifierDialog extends ModalDialog {

        private final JTextField nameTextField;

        public NewClassifierDialog(String title, String labelStr, String defaultValue) {
            super(SnapApp.getDefault().getMainFrame(), title, ModalDialog.ID_OK, null);

            final JPanel contentPane = new JPanel(new GridBagLayout());
            final GridBagConstraints gbc = GridBagUtils.createDefaultConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.gridx = 0;
            gbc.gridy = 0;


            nameTextField = new JTextField(defaultValue);
            nameTextField.setColumns(24);
            contentPane.add(new JLabel(labelStr), gbc);
            gbc.gridx = 1;
            contentPane.add(nameTextField, gbc);

            setContent(contentPane);
        }

        String getClassifierName() {
            return nameTextField.getText();
        }

        @Override
        protected void onOK() {
            hide();
        }
    }

    private static void createNewClassifier(final String classifierName, final CBIRSession session) {
        final ProgressHandleMonitor pm = ProgressHandleMonitor.create("Creating classifier");
        Runnable operation = () -> {
            pm.beginTask("Creating classifier...", 100);
            try {
                session.createClassifier(classifierName);
            } catch (Exception e) {
                SnapApp.getDefault().handleError("Failed to create classifier", e);
            } finally {
                pm.done();
            }
        };
        ProgressUtils.runOffEventThreadWithProgressDialog(operation, "Creating classifier", pm.getProgressHandle(), true, 50, 1000);
    }

    @Override
    public void notifySessionMsg(final CBIRSession.Notification msg, final Classifier classifier) {
        switch (msg) {
            case NewClassifier:
                final String name = classifier.getName();
                final DefaultListModel<String> model = (DefaultListModel<String>) classifierList.getModel();
                if (!model.contains(name)) {
                    model.addElement(name);
                    classifierList.setSelectedValue(name, true);
                }
                updateControls();
                break;
            case DeleteClassifier:
                initClassifierList();
                updateControls();
                break;
            case NewTrainingImages:
                break;
            case ModelTrained:
                updateControls();
                break;
        }
    }
}