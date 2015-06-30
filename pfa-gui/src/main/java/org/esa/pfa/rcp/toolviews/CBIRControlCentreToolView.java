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
package org.esa.pfa.rcp.toolviews;


import com.bc.ceres.core.SubProgressMonitor;
import com.jidesoft.swing.FolderChooser;
import org.esa.pfa.classifier.Classifier;
import org.esa.pfa.classifier.DatabaseManager;
import org.esa.pfa.search.CBIRSession;
import org.esa.snap.framework.ui.GridBagUtils;
import org.esa.snap.framework.ui.ModalDialog;
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
@ActionID(category = "Window", id = "org.esa.pfa.rcp.toolviews.CBIRControlCentreToolView")
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

    private final static Dimension preferredDimension = new Dimension(550, 300);
    private final static String PROPERTY_KEY_DB_PATH = "app.file.cbir.dbPath";
    private final static String PROPERTY_KEY_DB_REMOTE = "app.file.cbir.remoteAddress";
    private final static String PROPERTY_KEY_DB_ISREMOTE = "app.file.cbir.isRemote";
    private final static String PROPERTY_KEY_DB_APP = "app.file.cbir.appId";

    private JList<String> classifierList;
    private JButton newBtn, deleteBtn;
    private JButton queryBtn, labelBtn, applyBtn;
    private JTextField numTrainingImages;
    private JTextField numRetrievedImages;
    private JButton updateBtn;
    private JLabel iterationsLabel = new JLabel();
    private JTextField serviceLabel;
    private JTextField dbLabel;
    private JTextField appLabel;

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


        dbLabel = new JTextField("");
        dbLabel.setEnabled(false);
        dbLabel.setColumns(20);
        serviceLabel = new JTextField("");
        serviceLabel.setEnabled(false);
        serviceLabel.setColumns(20);
        appLabel = new JTextField("");
        appLabel.setEnabled(false);
        appLabel.setColumns(20);

        JButton dbSelectButton = new JButton(new AbstractAction("Service") {
            public void actionPerformed(ActionEvent e) {
                try {
                    final SelectDbDialog dlg = new SelectDbDialog(session);
                    dlg.show();

                    String localFolder = dlg.getLocalFolder();
                    String remoteAddress = dlg.getRemoteAddress();
                    String uri = dlg.isLocal() ? localFolder : remoteAddress;
                    String databaseName = dlg.getDatabaseName();
                    String application = dlg.getApplication();

                    serviceLabel.setText(uri);
                    dbLabel.setText(databaseName);
                    appLabel.setText(application);
                    initClassifierList();
                } catch (Throwable t) {
                    SnapApp.getDefault().handleError("Error connecting to database: "+t.getMessage(), t);
                }
            }
        });

        final JPanel servicePane = new JPanel(new GridBagLayout());
        final GridBagConstraints sgbc = GridBagUtils.createDefaultConstraints();
        sgbc.anchor = GridBagConstraints.NORTHWEST;

        sgbc.fill = GridBagConstraints.NONE;
        sgbc.gridx = 0;
        sgbc.gridy = 0;
        sgbc.gridwidth = 1;
        servicePane.add(new JLabel("Service: "), sgbc);

        sgbc.gridx = 1;
        sgbc.fill = GridBagConstraints.HORIZONTAL;
        sgbc.gridwidth = 3;
        servicePane.add(serviceLabel, sgbc);

        sgbc.gridx = 0;
        sgbc.gridy = 1;
        sgbc.gridwidth = 1;
        sgbc.fill = GridBagConstraints.NONE;
        servicePane.add(new JLabel("Database: "), sgbc);

        sgbc.gridx = 1;
        sgbc.fill = GridBagConstraints.HORIZONTAL;
        sgbc.gridwidth = 3;
        servicePane.add(dbLabel, sgbc);

        sgbc.gridx = 0;
        sgbc.gridy = 2;
        sgbc.gridwidth = 1;
        sgbc.fill = GridBagConstraints.NONE;
        servicePane.add(new JLabel("Application: "), sgbc);

        sgbc.gridx = 1;
        sgbc.fill = GridBagConstraints.HORIZONTAL;
        sgbc.gridwidth = 3;
        servicePane.add(appLabel, sgbc);

        sgbc.gridx = 4;
        sgbc.gridy = 0;
        sgbc.gridheight = 3;
        sgbc.gridwidth = 1;
        sgbc.fill = GridBagConstraints.VERTICAL;
        servicePane.add(dbSelectButton, sgbc);

        final GridBagConstraints gbc = GridBagUtils.createDefaultConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(servicePane, gbc);
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
        labelBtn = new JButton(new AbstractAction("Label") {
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
        applyBtn = new JButton(new AbstractAction("Apply") {
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
            final int numIterations = session.getNumIterations();
            numTrainingImages.setText(String.valueOf(session.getNumTrainingImages()));
            numRetrievedImages.setText(String.valueOf(session.getNumRetrievedImages()));
            iterationsLabel.setText(String.valueOf(numIterations));

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

    private static class SelectDbDialog extends ModalDialog {

        private final JTextField localFolder;
        private final JTextField remoteAddress;
        private final JComboBox<String> databaseCombo;
        private final JRadioButton isLocal;
        private final JRadioButton isRemote;
        private final JLabel application;

        private final CBIRSession session;
        private Exception error = null;

        public SelectDbDialog(final CBIRSession session) {
            super(SnapApp.getDefault().getMainFrame(), "Select Database", ModalDialog.ID_OK, null);
            this.session = session;

            final Preferences preferences = SnapApp.getDefault().getPreferences();
            String folderValue = preferences.get(PROPERTY_KEY_DB_PATH, "");
            String remoteValue = preferences.get(PROPERTY_KEY_DB_REMOTE, "http://localhost:8089/pfa/");
            String isRemoteValue = preferences.get(PROPERTY_KEY_DB_ISREMOTE, Boolean.FALSE.toString());

            boolean isRemoteb = Boolean.parseBoolean(isRemoteValue);
            ButtonGroup group = new ButtonGroup();
            isLocal = new JRadioButton("Local", !isRemoteb);
            isRemote = new JRadioButton("Remote", isRemoteb);
            group.add(isLocal);
            group.add(isRemote);

            localFolder = new JTextField(folderValue);
            remoteAddress = new JTextField(remoteValue);
            localFolder.setColumns(24);
            remoteAddress.setColumns(24);
            application = new JLabel();

            final ActionListener updateAction = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    update();
                }
            };
            localFolder.addActionListener(updateAction);
            remoteAddress.addActionListener(updateAction);
            isLocal.addActionListener(updateAction);
            isRemote.addActionListener(updateAction);

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
                    update();
                }
            });

            final JPanel contentPane = new JPanel(new GridBagLayout());
            final GridBagConstraints gbc = GridBagUtils.createDefaultConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.insets = new Insets(3,3,3,3);

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
            gbc.gridwidth = 1;

            ///////////

            gbc.gridx = 0;
            gbc.gridy = 3;
            contentPane.add(new Label("Database:"), gbc);
            databaseCombo = new JComboBox<>();
            databaseCombo.setEditable(false);
            databaseCombo.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    if(e.getStateChange() == ItemEvent.SELECTED) {
                        selectDatabase();
                    }
                }
            });

            gbc.gridx = 1;
            gbc.gridwidth = 2;
            contentPane.add(databaseCombo, gbc);
            gbc.gridwidth = 1;

            gbc.gridx = 0;
            gbc.gridy = 4;
            contentPane.add(new Label("Application:"), gbc);
            gbc.gridx = 1;
            gbc.gridwidth = 2;
            contentPane.add(application, gbc);

            update();

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

        String getDatabaseName() {
            return (String) databaseCombo.getSelectedItem();
        }

        String getApplication() {
            return application.getText();
        }

        private void update() {
            error = null;
            String localFolder = getLocalFolder();
            String remoteAddress = getRemoteAddress();
            String uri = isLocal() ? localFolder : remoteAddress;

            try {
                databaseCombo.setModel(new DefaultComboBoxModel<>());

                DatabaseManager databaseManager = session.createDatabaseManager(uri);
                for (String app : databaseManager.listDatabases()) {
                    System.out.println("app = " + app);
                    databaseCombo.addItem(app);
                }
                String appIdValue = SnapApp.getDefault().getPreferences().get(PROPERTY_KEY_DB_APP, "AlgalBloom");
                databaseCombo.setSelectedItem(appIdValue);

            } catch (Exception e) {
                error = e;
                //continue
            }
        }

        private void selectDatabase() {
            try {
                session.selectDatabase(getDatabaseName());
                application.setText(session.getApplicationDescriptor().getName());

            } catch (IOException e) {
                SnapApp.getDefault().handleError("Error selecting database: "+e.getMessage(), e);
            }
        }

        @Override
        protected void onOK() {
            if(error == null) {
                Preferences preferences = SnapApp.getDefault().getPreferences();
                preferences.put(PROPERTY_KEY_DB_PATH, getLocalFolder());
                preferences.put(PROPERTY_KEY_DB_REMOTE, getRemoteAddress());
                preferences.put(PROPERTY_KEY_DB_ISREMOTE, Boolean.toString(!isLocal()));
                preferences.put(PROPERTY_KEY_DB_APP, getDatabaseName());
                hide();
            } else {
                SnapDialogs.showError("Unable to connect to database: "+ error.toString());
            }
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