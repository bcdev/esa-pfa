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
import org.esa.pfa.fe.op.AttributeType;
import org.esa.pfa.fe.op.FeatureType;
import org.esa.pfa.gui.search.CBIRSession;
import org.esa.pfa.gui.toolviews.support.BrowserUtils;
import org.esa.pfa.gui.toolviews.support.OptionsControlPanel;
import org.esa.snap.rcp.SnapApp;
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

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.net.URISyntaxException;

@TopComponent.Description(
        preferredID = "DBSearchToolView",
        iconBase = "images/icons/pfa-database-24.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS //todo define
)
@TopComponent.Registration(
        mode = "navigator",
        openAtStartup = false,
        position = 11
)
@ActionID(category = "Window", id = "org.esa.pfa.gui.toolviews.DBSearchToolView")
@ActionReferences({
        @ActionReference(path = "Menu/View/Tool Windows/PFA"),
        @ActionReference(path = "Toolbars/PFA", position = 2)
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_DBSearchToolView_Name",
        preferredID = "DBSearchToolView"
)
@NbBundle.Messages({
        "CTL_DBSearchToolView_Name=DB Search",
})
/**
 * DB Search Toolview
 */
public class DBSearchToolView extends ToolTopComponent implements ActionListener, CBIRSession.Listener,
        OptionsControlPanel.Listener {

    private final CBIRSession session;

    private JLabel instructionLabel;
    private JTextField text;
    private JButton searchBtn, searchableFieldsBtn;

    private final static String querySyntaxURL = "https://lucene.apache.org/core/4_6_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description";
    private final static String QUERY_INSTRUCTION = "<html>"
            + "<b>Query Parser Syntax</b>: &lt field &gt:&lt term &gt or <br>" +
            "&lt field &gt:\"&lt phrase &gt\" or &lt field &gt:[&lt n1 &gt TO &lt n2 &gt]<br>" +
            "Multiple queries can be combined using AND, OR, NOT, +, - <br>" +
            "See <a href==\"" + querySyntaxURL + "\">here</a> for further syntax." +
            "</html>";

    public DBSearchToolView() {
        session = CBIRSession.getInstance();

        setLayout(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(4, 4, 4, 4));
        setDisplayName("PFA Search");
        add(createControl(), BorderLayout.CENTER);
        session.addListener(this);
    }

    public JComponent createControl() {

        final JPanel mainPane = new JPanel(new BorderLayout(5, 5));

        instructionLabel = new JLabel();
        mainPane.add(instructionLabel, BorderLayout.NORTH);

        final JPanel centrePanel = new JPanel(new GridBagLayout());
        final GridBagConstraints gbc = GridBagUtils.createDefaultConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridx = 0;
        gbc.gridy = 0;

        centrePanel.add(new JLabel("Database Query"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 3;
        text = new JTextField();
        text.setColumns(50);
        centrePanel.add(text, gbc);
        gbc.gridx = 1;
        gbc.gridy++;
        gbc.gridy++;

        final JLabel queryInstLabel = new JLabel(QUERY_INSTRUCTION);
        queryInstLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        queryInstLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                try {
                    BrowserUtils.openInBrowser(new URI(querySyntaxURL));
                } catch (URISyntaxException e) {
                    SnapApp.getDefault().handleError("Unable to follow link", e);
                }
            }
        });
        centrePanel.add(queryInstLabel, gbc);

        searchableFieldsBtn = new JButton("Searchable Fields");
        searchableFieldsBtn.setActionCommand("searchableFieldsBtn");
        searchableFieldsBtn.addActionListener(this);
        searchableFieldsBtn.setEnabled(false);
        gbc.gridy++;
        final JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
        btnPanel.add(searchableFieldsBtn);
        centrePanel.add(btnPanel, gbc);

        mainPane.add(centrePanel, BorderLayout.CENTER);

        final JPanel bottomPanel = new JPanel();
        searchBtn = new JButton("Search");
        searchBtn.setActionCommand("searchBtn");
        searchBtn.addActionListener(this);
        searchBtn.setEnabled(false);
        bottomPanel.add(searchBtn);
        mainPane.add(bottomPanel, BorderLayout.SOUTH);

        updateControls();

        return mainPane;
    }

    private void updateControls() {
        try {
            final boolean hasClassifier = session.hasClassifier();
            searchBtn.setEnabled(hasClassifier);
            searchableFieldsBtn.setEnabled(hasClassifier);

            if (hasClassifier) {
                instructionLabel.setText("");
            } else {
                instructionLabel.setText(OptionsControlPanel.USE_CONTROL_CENTRE_INSTRUCTION);
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
        try {
            final String command = event.getActionCommand();
            if (command.equals("searchableFieldsBtn")) {

                final String title = session.getApplicationDescriptor().getName() + " Searchable Fields";
                final SearchableFieldsDialog dlg = new SearchableFieldsDialog(title, "Fields:",
                                                                              getSearchableFields());
                dlg.show();

            } else if (command.equals("searchBtn")) {
                if (!session.hasClassifier()) {
                    return;
                }

                final String queryExpr = text.getText().trim();

                // create window if needed first and add to session listeners
                CBIRControlCentreToolView.showWindow("CBIRRetrievedImagesToolView");

                final ProgressHandleMonitor pm = ProgressHandleMonitor.create("Retrieving");
                Runnable operation = () -> {
                    pm.beginTask("Retrieving images...", 100);
                    try {
                        session.queryDatabase(queryExpr, pm);
                    } catch (Exception e) {
                        SnapApp.getDefault().handleError("Failed to retrieve images", e);
                    } finally {
                        pm.done();
                    }
                };
                ProgressUtils.runOffEventThreadWithProgressDialog(operation, "Retrieving images", pm.getProgressHandle(), true, 50, 1000);
            }
        } catch (Exception e) {
            SnapApp.getDefault().handleError("Error getting images", e);
        }
    }

    @Override
    public void notifySessionMsg(final CBIRSession.Notification msg, final Classifier classifier) {
        switch (msg) {
            case NewClassifier:
            case DeleteClassifier:
                updateControls();
                break;
        }
    }

    @Override
    public void notifyOptionsMsg(final OptionsControlPanel.Notification msg) {

    }

    private String getSearchableFields() {
        StringBuilder str = new StringBuilder();
        str.append("Searchable Fields:\n");
        str.append(format("product", String.class, "EO data product name"));
        str.append(format("px", Integer.TYPE, "Patch x-coordinate"));
        str.append(format("py", Integer.TYPE, "Patch y-coordinate"));
        for (FeatureType featureType : session.getApplicationDescriptor().getFeatureTypes()) {
            if (featureType.hasAttributes()) {
                AttributeType[] attributeTypes = featureType.getAttributeTypes();
                for (AttributeType attributeType : attributeTypes) {
                    str.append(format(featureType.getName() + "." + attributeType.getName(), attributeType));
                }
            } else {
                str.append(format(featureType.getName(), featureType));
            }
        }
        return str.toString();
    }

    private static String format(String fieldName, AttributeType attributeType) {
        return format(fieldName, attributeType.getValueType(), attributeType.getDescription());
    }

    private static String format(String fieldName, Class<?> valueType, String description) {
        return fieldName + ": [" + valueType + "] " + description + "\n";
    }

    private static class SearchableFieldsDialog extends ModalDialog {

        private final JTextArea textField;

        public SearchableFieldsDialog(String title, String labelStr, String defaultValue) {
            super(SnapApp.getDefault().getMainFrame(), title, ModalDialog.ID_OK, null);

            final JPanel contentPane = new JPanel(new GridBagLayout());
            final GridBagConstraints gbc = GridBagUtils.createDefaultConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.gridx = 0;
            gbc.gridy = 0;

            textField = new JTextArea(defaultValue);
            textField.setColumns(60);
            textField.setRows(20);
            contentPane.add(new JScrollPane(textField), gbc);

            setContent(contentPane);
        }

        @Override
        protected void onOK() {
            hide();
        }
    }
}