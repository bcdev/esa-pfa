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
import com.bc.ceres.core.SubProgressMonitor;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import com.jidesoft.swing.FolderChooser;
import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.ui.application.support.AbstractToolView;
import org.esa.beam.visat.VisatApp;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.search.CBIRSession;
import org.esa.pfa.search.SearchToolStub;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;


public class CBIRControlCentreToolView extends AbstractToolView implements CBIRSession.Listener {

    private final static Dimension preferredDimension = new Dimension(550, 300);
    private final static Font titleFont = new Font("Ariel", Font.BOLD, 14);
    private final static String title = "Content Based Image Retrieval";
    private final static String instructionsStr = "Select a feature extraction application";
    private final static String PROPERTY_KEY_DB_PATH = "app.file.cbir.dbPath";

    private JList<String> classifierList;
    private JButton newBtn, deleteBtn;
    private JButton queryBtn, trainBtn, applyBtn;
    private JTextField numTrainingImages;
    private JTextField numRetrievedImages;
    private JButton updateBtn;
    private JLabel iterationsLabel = new JLabel();

    private File dbFolder;
    private JTextField dbFolderTextField;

    private final CBIRSession session;

    public CBIRControlCentreToolView() {
        session = CBIRSession.getInstance();
        session.addListener(this);
    }

    public JComponent createControl() {

        final JPanel contentPane = new JPanel(new GridBagLayout());
        final GridBagConstraints gbc = GridBagUtils.createDefaultConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridx = 0;
        gbc.gridy = 0;


        dbFolder = new File(VisatApp.getApp().getPreferences().getPropertyString(PROPERTY_KEY_DB_PATH, ""));
        dbFolderTextField = new JTextField();
        if (dbFolder.exists()) {
            dbFolderTextField.setText(dbFolder.getAbsolutePath());
        }

        gbc.gridwidth = 1;
        gbc.gridy++;
        contentPane.add(new JLabel("Local database:"), gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        contentPane.add(dbFolderTextField, gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;

        final JButton fileChooserButton = new JButton(new AbstractAction("...") {
            @Override
            public void actionPerformed(ActionEvent event) {
                JFileChooser chooser = new FolderChooser();
                chooser.setDialogTitle("Find database folder");
                if (dbFolder.exists()) {
                    chooser.setCurrentDirectory(dbFolder.getParentFile());
                }
                final Window window = SwingUtilities.getWindowAncestor((JComponent) event.getSource());
                if (chooser.showDialog(window, "Select") == JFileChooser.APPROVE_OPTION) {
                    dbFolder = chooser.getSelectedFile();
                    dbFolderTextField.setText(dbFolder.getAbsolutePath());
                    VisatApp.getApp().getPreferences().setPropertyString(PROPERTY_KEY_DB_PATH, dbFolder.getAbsolutePath());
                    initClassifierList();
                }
            }
        });

        contentPane.add(fileChooserButton, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy += 2;
        contentPane.add(new JLabel("Saved Classifiers:"), gbc);
        gbc.gridy++;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy++;

        classifierList = new JList<>();
        classifierList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        classifierList.setLayoutOrientation(JList.VERTICAL);
        classifierList.setPrototypeCellValue("123456789012345678901234567890");
        classifierList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                try {
                    if (e.getValueIsAdjusting() == false) {
                        String classifierName = classifierList.getSelectedValue();
                        if (classifierName != null) {
                            SearchToolStub classifier = session.getClassifier();
                            if (classifier == null || !classifierName.equals(classifier.getClassifierName())) {
                                session.loadClassifier(dbFolder.getAbsolutePath(), classifierName);
                            }
                        }
                    }
                } catch (Throwable t) {
                    VisatApp.getApp().handleUnknownException(t);
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
                    VisatApp.getApp().handleUnknownException(t);
                }
            }
        });
        optionsPane.add(numTrainingImages, gbcOpt);

        gbcOpt.gridy++;
        gbcOpt.gridx = 0;
        gbcOpt.weightx = 1;
        optionsPane.add(new JLabel("# of retrieved images:"), gbcOpt);
        gbcOpt.gridx = 1;
        gbcOpt.weightx = 1;
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
                    VisatApp.getApp().handleUnknownException(t);
                }
            }
        });
        optionsPane.add(numRetrievedImages, gbcOpt);
        gbcOpt.gridy++;
        gbcOpt.gridx = 0;
        gbcOpt.weightx = 1;
        optionsPane.add(new JLabel("# of iterations:"), gbcOpt);
        gbcOpt.gridx = 1;
        gbcOpt.weightx = 1;
        optionsPane.add(iterationsLabel, gbcOpt);

        updateBtn = new JButton(new AbstractAction("Update") {
            public void actionPerformed(ActionEvent e) {
                try {
                    if (session.hasClassifier()) {
                        session.setNumTrainingImages(Integer.parseInt(numTrainingImages.getText()));
                        session.setNumRetrievedImages(Integer.parseInt(numRetrievedImages.getText()));
                    }
                } catch (Throwable t) {
                    VisatApp.getApp().handleUnknownException(t);
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

    @Override
    public void componentShown() {
        final Window win = getPaneWindow();
        if (win != null) {
            win.setPreferredSize(preferredDimension);
            win.setMaximumSize(preferredDimension);
            win.setSize(preferredDimension);
        }
    }

    private void initClassifierList() {
        final String[] savedClassifierNames = CBIRSession.getSavedClassifierNames(dbFolder.getAbsolutePath());
        final DefaultListModel<String> modelList = new DefaultListModel<>();
        for (String name : savedClassifierNames) {
            modelList.addElement(name);
        }
        classifierList.setModel(modelList);
        updateControls();
    }

    private JPanel createClassifierButtonPanel() {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        newBtn = new JButton(new AbstractAction("New") {
            public void actionPerformed(ActionEvent e) {
                try {
                    final PromptDialog dlg = new PromptDialog("New Classifier", "Name:", "");
                    dlg.show();

                    String classifierName = dlg.getClassifierName();
                    if (!classifierName.isEmpty()) {
                        String applicationName = dlg.getApplicationName();
                        PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
                        PFAApplicationDescriptor applicationDescriptor = applicationRegistry.getDescriptor(applicationName);
                        String dbPath = dbFolderTextField.getText();
                        createNewClassifier(applicationDescriptor, classifierName, dbPath);
                    }
                } catch (Throwable t) {
                    VisatApp.getApp().handleUnknownException(t);
                }
            }
        });
        deleteBtn = new JButton(new AbstractAction("Delete") {
            public void actionPerformed(ActionEvent e) {
                try {
                    session.deleteClassifier();
                } catch (Throwable t) {
                    VisatApp.getApp().handleUnknownException(t);
                }
            }
        });

        panel.add(newBtn);
        panel.add(deleteBtn);

        return panel;
    }

    private JPanel createSideButtonPanel() {
        final JPanel panel = new JPanel(new GridLayout(-1, 1, 2, 2));

        queryBtn = new JButton(new AbstractAction("Query") {
            public void actionPerformed(ActionEvent e) {
                try {
                    getContext().getPage().showToolView(CBIRQueryToolView.ID);
                } catch (Throwable t) {
                    VisatApp.getApp().handleUnknownException(t);
                }
            }
        });
        trainBtn = new JButton(new AbstractAction("Label") {
            public void actionPerformed(ActionEvent e) {
                if (!session.hasClassifier()) {
                    return;
                }
                try {
                    ProgressMonitorSwingWorker<Boolean, Void> worker = new ProgressMonitorSwingWorker<Boolean, Void>(getControl(), "Getting images to label") {
                        @Override
                        protected Boolean doInBackground(final ProgressMonitor pm) throws Exception {
                            pm.beginTask("Getting images...", 100);
                            try {
                                session.populateArchivePatches(SubProgressMonitor.create(pm, 50));
                                session.getImagesToLabel(SubProgressMonitor.create(pm, 50));
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
                    }
                } catch (Throwable t) {
                    VisatApp.getApp().handleUnknownException(t);
                }
            }
        });
        applyBtn = new JButton(new AbstractAction("Apply") {
            public void actionPerformed(ActionEvent e) {
                try {
                    if (!session.hasClassifier()) {
                        return;
                    }
                    ProgressMonitorSwingWorker<Boolean, Void> worker = new ProgressMonitorSwingWorker<Boolean, Void>(getControl(), "Retrieving") {
                        @Override
                        protected Boolean doInBackground(final ProgressMonitor pm) throws Exception {
                            pm.beginTask("Retrieving images...", 100);
                            try {
                                session.populateArchivePatches(SubProgressMonitor.create(pm, 50));  // not needed to train model but needed for next iteration
                                session.trainModel(SubProgressMonitor.create(pm, 50));
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
                        getContext().getPage().showToolView(CBIRRetrievedImagesToolView.ID);
                    }

                } catch (Throwable t) {
                    VisatApp.getApp().handleUnknownException(t);
                }
            }
        });

        panel.add(queryBtn);
        panel.add(trainBtn);
        panel.add(applyBtn);


        final JPanel panel2 = new JPanel(new BorderLayout(2, 2));
        panel2.add(panel, BorderLayout.NORTH);
        panel2.add(new JLabel(new ImageIcon(getClass().getResource("/images/pfa-logo-small.png"))), BorderLayout.SOUTH);
        return panel2;
    }

    private void updateControls() {
        newBtn.setEnabled(dbFolder.exists());

        final boolean hasActiveClassifier = session.hasClassifier();
        deleteBtn.setEnabled(hasActiveClassifier);

        numTrainingImages.setEnabled(hasActiveClassifier);
        numRetrievedImages.setEnabled(hasActiveClassifier);
        updateBtn.setEnabled(hasActiveClassifier);

        queryBtn.setEnabled(hasActiveClassifier);
        trainBtn.setEnabled(hasActiveClassifier);
        applyBtn.setEnabled(hasActiveClassifier);

        if (hasActiveClassifier) {
            final int numIterations = session.getNumIterations();
            numTrainingImages.setText(String.valueOf(session.getNumTrainingImages()));
            numRetrievedImages.setText(String.valueOf(session.getNumRetrievedImages()));
            iterationsLabel.setText(String.valueOf(numIterations));

            trainBtn.setEnabled(numIterations > 0);
            applyBtn.setEnabled(numIterations > 0);
        }
    }


    private static class PromptDialog extends ModalDialog {

        private final JTextField nameTextField;
        private final JComboBox<String> applicationCombo;

        public PromptDialog(String title, String labelStr, String defaultValue) {
            super(VisatApp.getApp().getMainFrame(), title, ModalDialog.ID_OK, null);

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

            gbc.gridx = 0;
            gbc.gridy = 1;
            contentPane.add(new Label("Application:"), gbc);

            applicationCombo = new JComboBox<>();
            for (PFAApplicationDescriptor app : PFAApplicationRegistry.getInstance().getAllDescriptors()) {
                applicationCombo.addItem(app.getName());
            }
            applicationCombo.setEditable(false);

            gbc.gridx = 1;
            contentPane.add(applicationCombo, gbc);

            setContent(contentPane);
        }

        String getClassifierName() {
            return nameTextField.getText();
        }

        String getApplicationName() {
            return (String) applicationCombo.getSelectedItem();
        }

        @Override
        protected void onOK() {
            hide();
        }
    }

    private static void createNewClassifier(final PFAApplicationDescriptor applicationDescriptor, final String classifierName, final String dbPath) {
        ProgressMonitorSwingWorker<Boolean, Void> worker = new ProgressMonitorSwingWorker<Boolean, Void>(VisatApp.getApp().getMainFrame(), "Loading") {
            @Override
            protected Boolean doInBackground(final ProgressMonitor pm) throws Exception {
                pm.beginTask("Creating classifier...", 100);
                try {
                    CBIRSession instance = CBIRSession.getInstance();
                    instance.createClassifier(classifierName, applicationDescriptor, dbPath, pm);
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
        try {
            worker.get();
        } catch (Exception e) {
            VisatApp.getApp().handleError("Failed to create new Classifier", e);
        }
    }

    @Override
    public void notifySessionMsg(final CBIRSession.Notification msg, final SearchToolStub classifier) {
        switch (msg) {
            case NewClassifier:
                final String name = classifier.getClassifierName();
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