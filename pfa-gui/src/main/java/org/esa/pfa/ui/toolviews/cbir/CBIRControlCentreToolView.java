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
import org.esa.pfa.classifier.ClassifierDelegate;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.search.CBIRSession;
import org.esa.snap.framework.ui.GridBagUtils;
import org.esa.snap.framework.ui.ModalDialog;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.windows.ToolTopComponent;
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
import java.io.File;
import java.util.prefs.Preferences;

@TopComponent.Description(
        preferredID = "CBIRControlCentreToolView",
        iconBase = "images/icons/pfa-manage-24.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS //todo define
)
@TopComponent.Registration(
        mode = "navigator",
        openAtStartup = false,
        position = 1
)
@ActionID(category = "Window", id = "org.esa.pfa.ui.toolviews.cbir.CBIRControlCentreToolView")
@ActionReferences({
        @ActionReference(path = "Menu/Window/Tool Windows"),
        @ActionReference(path = "Toolbars/PFA")
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

    private final static Dimension preferredDimension = new Dimension(550, 300);
    private final static String PROPERTY_KEY_DB_PATH = "app.file.cbir.dbPath";
    private final static String PROPERTY_KEY_DB_REMOTE = "app.file.cbir.remoteAddress";

    private JList<String> classifierList;
    private JButton newBtn, deleteBtn;
    private JButton queryBtn, trainBtn, applyBtn;
    private JTextField numTrainingImages;
    private JTextField numRetrievedImages;
    private JButton updateBtn;
    private JLabel iterationsLabel = new JLabel();
    private JLabel dbLabel;

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
        dbLabel = new JLabel("");

        final JPanel contentPane = new JPanel(new GridBagLayout());
        final GridBagConstraints gbc = GridBagUtils.createDefaultConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridx = 0;
        gbc.gridy = 0;

        gbc.gridwidth = 1;
        gbc.gridy++;
        contentPane.add(new JLabel("DB:"), gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        contentPane.add(dbLabel, gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;

        JButton dbSelectButton = new JButton(new AbstractAction("Select DB") {
            public void actionPerformed(ActionEvent e) {
                try {
                    Preferences preferences = SnapApp.getDefault().getPreferences();
                    String folderValue = preferences.get(PROPERTY_KEY_DB_PATH, "");
                    String remoteValue = preferences.get(PROPERTY_KEY_DB_REMOTE, "");
                    final SelectDbDialog dlg = new SelectDbDialog(folderValue, remoteValue);
                    dlg.show();

                    String localFolder = dlg.getLocalFolder();
                    String remoteAddress = dlg.getRemoteAddress();
                    String uri = dlg.isLocal() ? localFolder : remoteAddress;
                    String applicationName = dlg.getApplicationName();

                    PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
                    PFAApplicationDescriptor applicationDescriptor = applicationRegistry.getDescriptorByName(applicationName);
                    session.createClassifierManager(uri, applicationDescriptor.getId());

                    dbLabel.setText(applicationName + ":" + uri);
                    initClassifierList();
                } catch (Throwable t) {
                    SnapApp.getDefault().handleError("Error creating new classifier", t);
                }
            }
        });

        contentPane.add(dbSelectButton, gbc);

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
        classifierList.setPrototypeCellValue("12345678901234567890");
        classifierList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                try {
                    if (!e.getValueIsAdjusting()) {
                        String classifierName = classifierList.getSelectedValue();
                        if (classifierName != null) {
                            ClassifierDelegate classifier = session.getClassifier();
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
                    SnapApp.getDefault().handleError("Error setting retrieved images", t);
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

    //todo @Override
    public void componentShown() {
        final Window win = SwingUtilities.getWindowAncestor(this);
        if (win != null) {
            win.setPreferredSize(preferredDimension);
            win.setMaximumSize(preferredDimension);
            win.setSize(preferredDimension);
        }
    }

    private void initClassifierList() {
        final DefaultListModel<String> modelList = new DefaultListModel<>();
        for (String name : session.listClassifiers()) {
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
        final Window parentWindow = SwingUtilities.getWindowAncestor(this);

        queryBtn = new JButton(new AbstractAction("Query") {
            public void actionPerformed(ActionEvent e) {
                try {
                    showWindow("CBIRQueryToolView");
                } catch (Throwable t) {
                    SnapApp.getDefault().handleError("Error calling Query", t);
                }
            }
        });
        trainBtn = new JButton(new AbstractAction("Label") {
            public void actionPerformed(ActionEvent e) {
                if (!session.hasClassifier()) {
                    return;
                }
                try {
                    ProgressMonitorSwingWorker<Boolean, Void> worker =
                            new ProgressMonitorSwingWorker<Boolean, Void>(parentWindow, "Getting images to label") {
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
                        showWindow("CBIRLabelingToolView");
                    }
                } catch (Throwable t) {
                    SnapApp.getDefault().handleError("Error getting images", t);
                }
            }
        });
        applyBtn = new JButton(new AbstractAction("Apply") {
            public void actionPerformed(ActionEvent e) {
                try {
                    if (!session.hasClassifier()) {
                        return;
                    }
                    ProgressMonitorSwingWorker<Boolean, Void> worker =
                            new ProgressMonitorSwingWorker<Boolean, Void>(parentWindow, "Retrieving") {
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
                        showWindow("CBIRRetrievedImagesToolView");
                    }

                } catch (Throwable t) {
                    SnapApp.getDefault().handleError("Error retrieving images", t);
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
        newBtn.setEnabled(session.hasClassifierManager());

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

    private static class SelectDbDialog extends ModalDialog {

        private final JTextField localFolder;
        private final JTextField remoteAddress;
        private final JComboBox<String> applicationCombo;
        private final JRadioButton isLocal;
        private final JRadioButton isRemote;

        public SelectDbDialog(String folderValue, String remoteValue) {
            super(SnapApp.getDefault().getMainFrame(), "Select Database", ModalDialog.ID_OK, null);

            applicationCombo = new JComboBox<>();
            for (PFAApplicationDescriptor app : PFAApplicationRegistry.getInstance().getAllDescriptors()) {
                applicationCombo.addItem(app.getName());
            }
            applicationCombo.setEditable(false);

            ButtonGroup group = new ButtonGroup();
            isLocal = new JRadioButton("Local", true);
            isRemote = new JRadioButton("Remote", false);
            group.add(isLocal);
            group.add(isRemote);

            localFolder = new JTextField(folderValue);
            remoteAddress = new JTextField(remoteValue);
            localFolder.setColumns(24);
            remoteAddress.setColumns(24);

            folderValue = folderValue != null ? folderValue : "";
            final File dbFolder = new File(folderValue);

            final JButton fileChooserButton = new JButton(new AbstractAction("...") {
                @Override
                public void actionPerformed(ActionEvent event) {
                    FolderChooser chooser = new FolderChooser();
                    chooser.setDialogTitle("Find database folder");
                    if (dbFolder.exists()) {
                        chooser.setSelectedFolder(dbFolder);
                    }
                    final Window window = SwingUtilities.getWindowAncestor((JComponent) event.getSource());
                    if (chooser.showDialog(window, "Select") == JFileChooser.APPROVE_OPTION) {
                        File selectedFolder = chooser.getSelectedFolder();
                        localFolder.setText(selectedFolder.getAbsolutePath());

                    }
                }
            });

            final JPanel contentPane = new JPanel(new GridBagLayout());
            final GridBagConstraints gbc = GridBagUtils.createDefaultConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.gridx = 0;
            gbc.gridy = 0;

            contentPane.add(new Label("Application:"), gbc);

            gbc.gridx = 1;
            gbc.gridwidth = 2;
            contentPane.add(applicationCombo, gbc);
            gbc.gridwidth = 1;

            //////////

            gbc.gridx = 0;
            gbc.gridy = 1;
            contentPane.add(this.isLocal, gbc);
            gbc.gridx = 1;
            contentPane.add(localFolder, gbc);
            gbc.gridx = 2;
            contentPane.add(fileChooserButton, gbc);

            //////////

            gbc.gridx = 0;
            gbc.gridy = 2;
            contentPane.add(isRemote, gbc);

            gbc.gridx = 1;
            gbc.gridwidth = 2;
            contentPane.add(remoteAddress, gbc);

            setContent(contentPane);
        }

        boolean isLocal() {
            return isLocal.isSelected();
        }

        String getLocalFolder() {
            return localFolder.getText();
        }

        String getRemoteAddress() {
            return remoteAddress.getText();
        }

        String getApplicationName() {
            return (String) applicationCombo.getSelectedItem();
        }

        @Override
        protected void onOK() {
            Preferences preferences = SnapApp.getDefault().getPreferences();
            preferences.put(PROPERTY_KEY_DB_PATH, getLocalFolder());
            preferences.put(PROPERTY_KEY_DB_REMOTE, getRemoteAddress());
            hide();
        }
    }

    private static void createNewClassifier(final String classifierName, final CBIRSession session) {
        ProgressMonitorSwingWorker<Boolean, Void> worker = new ProgressMonitorSwingWorker<Boolean, Void>(SnapApp.getDefault().getMainFrame(), "Loading") {
            @Override
            protected Boolean doInBackground(final ProgressMonitor pm) throws Exception {
                pm.beginTask("Creating classifier...", 100);
                try {
                    session.createClassifier(classifierName);
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
            SnapApp.getDefault().handleError("Failed to create new Classifier", e);
        }
    }

    @Override
    public void notifySessionMsg(final CBIRSession.Notification msg, final ClassifierDelegate classifier) {
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