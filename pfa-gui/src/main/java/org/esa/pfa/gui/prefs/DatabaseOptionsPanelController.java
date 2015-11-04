/*
 * Copyright (C) 2015 Brockmann Consult GmbH (info@brockmann-consult.de)
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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esa.pfa.gui.prefs;

import com.bc.ceres.binding.PropertyDescriptor;
import com.bc.ceres.binding.PropertySet;
import com.bc.ceres.binding.ValueSet;
import com.bc.ceres.swing.binding.BindingContext;
import com.jidesoft.swing.FolderChooser;
import org.esa.pfa.classifier.ClassifierManager;
import org.esa.pfa.classifier.DatabaseManager;
import org.esa.pfa.classifier.LocalDatabaseManager;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.pfa.gui.search.CBIRSession;
import org.esa.pfa.ws.RestDatabaseManager;
import org.esa.snap.core.util.Debug;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.preferences.DefaultConfigController;
import org.esa.snap.rcp.preferences.Preference;
import org.esa.snap.ui.GridBagUtils;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

@OptionsPanelController.SubRegistration(
        location = "ESA_PFA",
        displayName = "Database",
        keywords = "pfa",
        keywordsCategory = "ESA PFA/Database",
        id = "Database"
)
public final class DatabaseOptionsPanelController extends DefaultConfigController {

    private static final String DB_IS_LOCAL = "local";
    private static final String DB_IS_REMOTE = "remote";

    private static final String PREFERENCE_DB_LOCAL_PATH = "pfa.cbir.dbPath";
    private static final String PREFERENCE_DB_LOCAL_PATH_DEFAULT = "";
    private static final String PREFERENCE_DB_REMOTE_URI = "pfa.cbir.remoteAddress";
    private static final String PREFERENCE_DB_REMOTE_URI_DEFAULT = "http://www.brockmann-consult.de/pfa/ws";
    private static final String PREFERENCE_DB_REMOTE_OR_LOCAL = "pfa.cbir.remoteOrLocal";
    private static final String PREFERENCE_DB_REMOTE_OR_LOCAL_DEFAULT = DB_IS_REMOTE;
    private static final String PREFERENCE_DB_NAME = "pfa.cbir.dbName";
    private static final String PREFERENCE_DB_NAME_DEFAULT = "";

    private DatabaseManager databaseManager;

    @Override
    protected JPanel createPanel(BindingContext context) {
        JRadioButton isLocal = new JRadioButton("Local");
        JRadioButton isRemote = new JRadioButton("Remote");
        ButtonGroup group = new ButtonGroup();
        group.add(isLocal);
        group.add(isRemote);

        JTextField localFolder = new JTextField(24);
        JTextField remoteAddress = new JTextField(24);
        JLabel application = new JLabel();

        final JButton fileChooserButton = new JButton(new ChooseDbDirAction(context));

        JComboBox<String> databaseCombo = new JComboBox<>();
        databaseCombo.setEditable(false);


        Map<AbstractButton, Object> valueSet = new HashMap<>(2);
        valueSet.put(isLocal, DB_IS_LOCAL);
        valueSet.put(isRemote, DB_IS_REMOTE);
        context.bind(PREFERENCE_DB_REMOTE_OR_LOCAL, group, valueSet);

        context.bind(PREFERENCE_DB_LOCAL_PATH, localFolder);
        context.bind(PREFERENCE_DB_REMOTE_URI, remoteAddress);

        context.bind(PREFERENCE_DB_NAME, databaseCombo);
        PropertyChangeListener updateDbPCL = evt -> updateDatabaseCombo(context.getPropertySet());
        context.addPropertyChangeListener(PREFERENCE_DB_LOCAL_PATH, updateDbPCL);
        context.addPropertyChangeListener(PREFERENCE_DB_REMOTE_URI, updateDbPCL);
        context.addPropertyChangeListener(PREFERENCE_DB_REMOTE_OR_LOCAL, updateDbPCL);

        context.addPropertyChangeListener(PREFERENCE_DB_NAME, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String databaseName = (String) evt.getNewValue();
                try {
                    if (databaseManager != null && databaseName != null) {
                        ClassifierManager classifierManager = databaseManager.createClassifierManager(databaseName);
                        String applicationId = classifierManager.getApplicationId();
                        PFAApplicationRegistry applicationRegistry = PFAApplicationRegistry.getInstance();
                        PFAApplicationDescriptor appDescriptor = applicationRegistry.getDescriptorById(applicationId);
                        String appDescriptorName = appDescriptor.getName();
                        application.setText(appDescriptorName);
                    } else {
                        application.setText("");
                    }
                } catch (IOException e) {
                    SnapApp.getDefault().handleError("Error selecting database: " + e.getMessage(), e);
                    application.setText("");
                }
            }
        });

        context.getBinding(PREFERENCE_DB_LOCAL_PATH).addComponent(fileChooserButton);
        context.bindEnabledState(PREFERENCE_DB_LOCAL_PATH, true, PREFERENCE_DB_REMOTE_OR_LOCAL, DB_IS_LOCAL);
        context.bindEnabledState(PREFERENCE_DB_LOCAL_PATH, true, PREFERENCE_DB_REMOTE_OR_LOCAL, DB_IS_LOCAL);
        context.bindEnabledState(PREFERENCE_DB_REMOTE_URI, true, PREFERENCE_DB_REMOTE_OR_LOCAL, DB_IS_REMOTE);

        final JPanel contentPane = new JPanel(new GridBagLayout());
        final GridBagConstraints gbc = GridBagUtils.createDefaultConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(3, 3, 3, 3);

        gbc.gridx = 0;
        gbc.gridy = 1;
        contentPane.add(isLocal, gbc);
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

        updateDatabaseCombo(context.getPropertySet());

        return contentPane;
    }

    private void updateDatabaseCombo(PropertySet propertySet) {
        String localFolder = propertySet.getValue(PREFERENCE_DB_LOCAL_PATH);
        String remoteAddress = propertySet.getValue(PREFERENCE_DB_REMOTE_URI);
        String remoteOrLocal = propertySet.getValue(PREFERENCE_DB_REMOTE_OR_LOCAL);
        String uri = DB_IS_REMOTE.equals(remoteOrLocal) ? remoteAddress: localFolder;

        try {
            URI databaseManagerURI = getDatabaseManagerURI(uri);
            if (databaseManager == null || !databaseManager.getURI().equals(databaseManagerURI)) {
                databaseManager = createDatabaseManager(databaseManagerURI);
            }
            PropertyDescriptor dbNameProperty = propertySet.getProperty(PREFERENCE_DB_NAME).getDescriptor();
            if (databaseManager.isAlive()) {
                String[] databases = databaseManager.listDatabases();
                dbNameProperty.setValueSet(new ValueSet(databases));
            } else {
                dbNameProperty.setValueSet(new ValueSet(new String[0]));
                SnapApp.getDefault().handleError("Failed to connect to Database.", null);
            }
        } catch (URISyntaxException|IOException|IllegalArgumentException e) {
            SnapApp.getDefault().handleError("Error reading applications:" + e.getMessage(), e);
        }
    }

    @Override
    public void applyChanges() {
        super.applyChanges();
        initSessionFromPreferences(CBIRSession.getInstance());
    }

    protected PropertySet createPropertySet() {
        return createPropertySet(new PfaDatabaseBean());
    }

    @Override
    public HelpCtx getHelpCtx() {
        return null;
    }

    private static DatabaseManager createDatabaseManager(URI uri) throws IOException {
        String scheme = uri.getScheme();
        if ("http".equals(scheme)) {
            // if HTTP URL: Web Service Client
            return new RestDatabaseManager(uri);
        } else if ("file".equals(scheme)) {
            // if file URL
            return new LocalDatabaseManager(uri);
        }
        throw new IllegalArgumentException("Unsupported DatabaseManager URI: " + uri);
    }


    private static URI getDatabaseManagerURI(String uriString) throws URISyntaxException {
        if (uriString.startsWith("http")) {
            // if HTTP URL: Web Service Client
            return new URI(uriString);
        } else {
            // if file URL
            URI uri;
            if (uriString.startsWith("file:")) {
                return new URI(uriString);
            } else {
                File file = new File(uriString);
                return file.toURI();
            }
        }
    }

    public static void initSessionFromPreferences(CBIRSession session) {
        Preferences preferences = SnapApp.getDefault().getPreferences();
        String dbName = preferences.get(PREFERENCE_DB_NAME, PREFERENCE_DB_NAME_DEFAULT);
        if (dbName.equals(PREFERENCE_DB_NAME_DEFAULT)) {
            Debug.trace("no database selected");
            return;
        }

        String localFolder = preferences.get(PREFERENCE_DB_LOCAL_PATH, PREFERENCE_DB_LOCAL_PATH_DEFAULT);
        String remoteAddress = preferences.get(PREFERENCE_DB_REMOTE_URI, PREFERENCE_DB_REMOTE_URI_DEFAULT);
        String remoteOrLocal = preferences.get(PREFERENCE_DB_REMOTE_OR_LOCAL, PREFERENCE_DB_REMOTE_OR_LOCAL_DEFAULT);
        String uri = DB_IS_REMOTE.equals(remoteOrLocal) ? remoteAddress: localFolder;

        try {
            URI databaseManagerURI = getDatabaseManagerURI(uri);
            DatabaseManager dbManager = createDatabaseManager(databaseManagerURI);
            if (dbManager.isAlive()) {
                ClassifierManager classifierManager = dbManager.createClassifierManager(dbName);
                session.setClassifierManager(classifierManager);
            }
        } catch (URISyntaxException|IOException|IllegalArgumentException e) {
            SnapApp.getDefault().handleError("Error reading applications:" + e.getMessage(), e);
        }
    }

    static class PfaDatabaseBean {

        @Preference(label = "Local", key = PREFERENCE_DB_LOCAL_PATH)
        String localDbPath = PREFERENCE_DB_LOCAL_PATH_DEFAULT;

        @Preference(label = "Remote", key = PREFERENCE_DB_REMOTE_URI)
        String remoteAddress = PREFERENCE_DB_REMOTE_URI_DEFAULT;

        @Preference(label = "Local", key = PREFERENCE_DB_NAME)
        String databaseName = PREFERENCE_DB_NAME_DEFAULT;

        @Preference(label = "Remote or Local", key = PREFERENCE_DB_REMOTE_OR_LOCAL)
        String remoteOrLocal = PREFERENCE_DB_REMOTE_OR_LOCAL_DEFAULT;
    }

    private static class ChooseDbDirAction extends AbstractAction {

        private final BindingContext context;

        public ChooseDbDirAction(BindingContext context) {
            super("...");
            this.context = context;
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            PropertySet propertySet = context.getPropertySet();

            FolderChooser chooser = new FolderChooser();
            chooser.setDialogTitle("Find database folder");
            String value = propertySet.getValue(PREFERENCE_DB_LOCAL_PATH);
            File dbFolder = new File(value);
            if (dbFolder.exists()) {
                chooser.setSelectedFolder(dbFolder);
            }
            final Window window = SwingUtilities.getWindowAncestor((JComponent) event.getSource());
            if (chooser.showDialog(window, "Select") == JFileChooser.APPROVE_OPTION) {
                File selectedFolder = chooser.getSelectedFolder();
                propertySet.setValue(PREFERENCE_DB_LOCAL_PATH, selectedFolder.getAbsolutePath());
            }
        }
    }
}
