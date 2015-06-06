/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esa.pfa.rcp.prefs;

import java.io.File;
import java.util.Arrays;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.DefaultListModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.esa.snap.rcp.SnapApp;
import org.openide.util.NbPreferences;

final class ProductAccessPanel extends javax.swing.JPanel {

    private final ProductAccessOptionsPanelController controller;
    
    private final DefaultListModel<String> pathsListModel = new DefaultListModel<String>();
    
    private final Preferences prefs = NbPreferences.forModule(ProductAccessPanel.class);

    ProductAccessPanel(ProductAccessOptionsPanelController controller) {
        this.controller = controller;
        initComponents();
        // listen to changes in form fields and call controller.changed()
        pathsListModel.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                controller.changed();
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                controller.changed();
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                controller.changed();
            }
        });
        DocumentListener documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                controller.changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                controller.changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                controller.changed();
            }
        };
        customCliRadioButton.addItemListener(e -> controller.changed());
        customCliTextArea.getDocument().addDocumentListener(documentListener);
        defaultUrlTextField.getDocument().addDocumentListener(documentListener);
        localPathsCheckBox.addItemListener(e -> controller.changed());
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        accessMethodButtonGroup = new javax.swing.ButtonGroup();
        defaultUrlRadioButton = new javax.swing.JRadioButton();
        customCliRadioButton = new javax.swing.JRadioButton();
        defaultUrlTextField = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        customCliTextArea = new javax.swing.JTextArea();
        jScrollPane2 = new javax.swing.JScrollPane();
        localPathsList = new javax.swing.JList();
        localPathsAddButton = new javax.swing.JButton();
        localPathsCheckBox = new javax.swing.JCheckBox();
        localPathsEditButton = new javax.swing.JButton();
        localPathsDeleteButton = new javax.swing.JButton();

        accessMethodButtonGroup.add(defaultUrlRadioButton);
        defaultUrlRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(defaultUrlRadioButton, org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.defaultUrlRadioButton.text")); // NOI18N

        accessMethodButtonGroup.add(customCliRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(customCliRadioButton, org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.customCliRadioButton.text")); // NOI18N

        defaultUrlTextField.setText(org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.defaultUrl.text")); // NOI18N
        defaultUrlTextField.setName("defaultUrl"); // NOI18N

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, defaultUrlRadioButton, org.jdesktop.beansbinding.ELProperty.create("${selected}"), defaultUrlTextField, org.jdesktop.beansbinding.BeanProperty.create("editable"));
        bindingGroup.addBinding(binding);

        customCliTextArea.setColumns(20);
        customCliTextArea.setRows(5);
        customCliTextArea.setName("customCli"); // NOI18N

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, customCliRadioButton, org.jdesktop.beansbinding.ELProperty.create("${selected}"), customCliTextArea, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        jScrollPane1.setViewportView(customCliTextArea);

        localPathsList.setModel(pathsListModel);
        localPathsList.setName("localPaths"); // NOI18N
        localPathsList.setOpaque(false);
        jScrollPane2.setViewportView(localPathsList);

        org.openide.awt.Mnemonics.setLocalizedText(localPathsAddButton, org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.localPathsAdd.text")); // NOI18N
        localPathsAddButton.setName("localPathsAdd"); // NOI18N
        localPathsAddButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                localPathsAddButtonActionPerformed(evt);
            }
        });

        localPathsCheckBox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(localPathsCheckBox, org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.localPathsCheckBox.text")); // NOI18N
        localPathsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                localPathsCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(localPathsEditButton, org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.localPathsEditButton.text")); // NOI18N
        localPathsEditButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                localPathsEditButtonActionPerformed(evt);
            }
        });

        localPathsDeleteButton.setText(org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.localPathsDeleteButton.text")); // NOI18N
        localPathsDeleteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                localPathsDeleteButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(defaultUrlRadioButton)
                    .addComponent(customCliRadioButton)
                    .addComponent(localPathsCheckBox))
                .addGap(0, 0, Short.MAX_VALUE))
            .addGroup(layout.createSequentialGroup()
                .addGap(21, 21, 21)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(defaultUrlTextField, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 463, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(localPathsAddButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(localPathsDeleteButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(localPathsEditButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(defaultUrlRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(defaultUrlTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addComponent(customCliRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(7, 7, 7)
                .addComponent(localPathsCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 88, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(localPathsAddButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(localPathsEditButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(localPathsDeleteButton)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void localPathsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_localPathsCheckBoxActionPerformed
    }//GEN-LAST:event_localPathsCheckBoxActionPerformed

    private void localPathsListKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_localPathsListKeyTyped
    }//GEN-LAST:event_localPathsListKeyTyped

    private void localPathsAddButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_localPathsAddButtonActionPerformed
        AddLocalPathDialog dialog = new AddLocalPathDialog(SwingUtilities.windowForComponent(this));
        dialog.setVisible(true);
        if (dialog.getReturnStatus() == AddLocalPathDialog.RET_OK) {
            final String path = dialog.getSelectedPath();
            pathsListModel.addElement(path);
        }
    }//GEN-LAST:event_localPathsAddButtonActionPerformed

    private void localPathsEditButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_localPathsEditButtonActionPerformed
        final int index = localPathsList.getSelectedIndex();
        if (index >= 0) {
            String path = pathsListModel.get(index);
            AddLocalPathDialog dialog = new AddLocalPathDialog(SwingUtilities.windowForComponent(this));
            dialog.setSelectedPath(path);
            dialog.setVisible(true);
            if (dialog.getReturnStatus() == AddLocalPathDialog.RET_OK) {
                path = dialog.getSelectedPath();
                pathsListModel.set(index, path);
            }
        }
    }//GEN-LAST:event_localPathsEditButtonActionPerformed

    private void localPathsDeleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_localPathsDeleteButtonActionPerformed
        final int[] selectedIndices = localPathsList.getSelectedIndices();
        Arrays.sort(selectedIndices);
        for (int index = selectedIndices.length - 1; index >= 0; index--) {
            pathsListModel.remove(index);
        }
    }//GEN-LAST:event_localPathsDeleteButtonActionPerformed

    void load() {
        String defaultUrl = prefs.get("productAccess.defaultUrl", "");
        String customCli = prefs.get("productAccess.customCli", "");
        String localPaths = prefs.get("productAccess.localPaths", "");
        boolean customCliEnabled = prefs.getBoolean("productAccess.customCli.enabled",
                defaultUrlTextField.getText().isEmpty());
        boolean localPathsEnabled = prefs.getBoolean("productAccess.localPaths.enabled",
                !pathsListModel.isEmpty());
        
        defaultUrlTextField.setText(defaultUrl);
        customCliTextArea.setText(customCli);
        customCliRadioButton.setSelected(customCliEnabled);
        localPathsCheckBox.setSelected(localPathsEnabled);
        String[] paths = localPaths.split(File.pathSeparator);
        pathsListModel.removeAllElements();
        for (String path : paths) {
            pathsListModel.addElement(path);
        }
    }

    void store() {
        String localPaths = Stream.of(pathsListModel.toArray()).map(Object::toString)
                .collect(Collectors.joining(File.pathSeparator));

        prefs.put("productAccess.defaultUrl", defaultUrlTextField.getText());
        prefs.put("productAccess.customCli", customCliTextArea.getText());
        prefs.put("productAccess.localPaths", localPaths);
        prefs.putBoolean("productAccess.customCli.enabled", customCliRadioButton.isSelected());
        prefs.putBoolean("productAccess.localPaths.enabled", localPathsCheckBox.isSelected());

        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            SnapApp.getDefault().getLogger().severe(e.getMessage());
        }
    }

    boolean valid() {
        // Check whether form is consistent and complete
        return true;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup accessMethodButtonGroup;
    private javax.swing.JRadioButton customCliRadioButton;
    private javax.swing.JTextArea customCliTextArea;
    private javax.swing.JRadioButton defaultUrlRadioButton;
    private javax.swing.JTextField defaultUrlTextField;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JButton localPathsAddButton;
    private javax.swing.JCheckBox localPathsCheckBox;
    private javax.swing.JButton localPathsDeleteButton;
    private javax.swing.JButton localPathsEditButton;
    private javax.swing.JList localPathsList;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables
}