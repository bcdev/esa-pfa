/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esa.pfa.rcp.prefs;

import org.esa.pfa.ordering.ProductAccessOptions;
import org.esa.snap.rcp.SnapApp;
import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.ELProperty;
import org.openide.util.NbBundle;

import javax.swing.DefaultListModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.io.File;
import java.util.Arrays;
import java.util.prefs.BackingStoreException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.jdesktop.beansbinding.Bindings.createAutoBinding;

final class ProductAccessPanel extends javax.swing.JPanel {

    private final ProductAccessOptionsPanelController controller;

    private final DefaultListModel<String> localPathsListModel = new DefaultListModel<String>();

    private javax.swing.ButtonGroup accessMethodButtonGroup;
    private javax.swing.JRadioButton customClRadioButton;
    private javax.swing.JTextArea customClCodeTextArea;
    private javax.swing.JRadioButton defaultUrlRadioButton;
    private javax.swing.JTextField defaultUrlTextField;
    private javax.swing.JLabel workingDirLabel;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextField customClWorkingDirTextField;
    private javax.swing.JButton localPathsAddButton;
    private javax.swing.JCheckBox localPathsCheckBox;
    private javax.swing.JButton localPathsDeleteButton;
    private javax.swing.JButton localPathsEditButton;
    private javax.swing.JList localPathsList;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;

    ProductAccessPanel(ProductAccessOptionsPanelController controller) {
        this.controller = controller;
        initComponents();
        // listen to changes in form fields and call controller.changed()
        localPathsListModel.addListDataListener(new ListDataListener() {
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
        customClRadioButton.addItemListener(e -> controller.changed());
        customClCodeTextArea.getDocument().addDocumentListener(documentListener);
        defaultUrlTextField.getDocument().addDocumentListener(documentListener);
        localPathsCheckBox.addItemListener(e -> controller.changed());
    }

    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        accessMethodButtonGroup = new javax.swing.ButtonGroup();
        defaultUrlRadioButton = new javax.swing.JRadioButton();
        customClRadioButton = new javax.swing.JRadioButton();
        defaultUrlTextField = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        customClCodeTextArea = new javax.swing.JTextArea();
        jScrollPane2 = new javax.swing.JScrollPane();
        localPathsList = new javax.swing.JList();
        localPathsAddButton = new javax.swing.JButton();
        localPathsCheckBox = new javax.swing.JCheckBox();
        localPathsEditButton = new javax.swing.JButton();
        localPathsDeleteButton = new javax.swing.JButton();
        workingDirLabel = new javax.swing.JLabel();
        customClWorkingDirTextField = new javax.swing.JTextField();

        accessMethodButtonGroup.add(defaultUrlRadioButton);
        defaultUrlRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(defaultUrlRadioButton, org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.defaultUrlRadioButton.text")); // NOI18N

        accessMethodButtonGroup.add(customClRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(customClRadioButton, org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.customCliRadioButton.text")); // NOI18N

        defaultUrlTextField.setText(org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.defaultUrl.text")); // NOI18N
        defaultUrlTextField.setName("defaultUrl"); // NOI18N

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, defaultUrlRadioButton, org.jdesktop.beansbinding.ELProperty.create("${selected}"), defaultUrlTextField, org.jdesktop.beansbinding.BeanProperty.create("editable"));
        bindingGroup.addBinding(binding);

        customClCodeTextArea.setColumns(20);
        customClCodeTextArea.setRows(5);
        customClCodeTextArea.setName("customCli"); // NOI18N

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, customClRadioButton, org.jdesktop.beansbinding.ELProperty.create("${selected}"), customClCodeTextArea, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        jScrollPane1.setViewportView(customClCodeTextArea);

        org.openide.awt.Mnemonics.setLocalizedText(workingDirLabel, org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.workingDirLabel.text")); // NOI18N
        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, customClRadioButton, org.jdesktop.beansbinding.ELProperty.create("${selected}"), workingDirLabel, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        customClWorkingDirTextField.setText(org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.workingDirTextField.text")); // NOI18N
        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, customClRadioButton, org.jdesktop.beansbinding.ELProperty.create("${selected}"), customClWorkingDirTextField, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        localPathsCheckBox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(localPathsCheckBox, NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.localPathsCheckBox.text")); // NOI18N

        localPathsList.setModel(localPathsListModel);
        localPathsList.setName("localPaths"); // NOI18N
        localPathsList.setOpaque(false);
        jScrollPane2.setViewportView(localPathsList);
        binding = createAutoBinding(AutoBinding.UpdateStrategy.READ_WRITE, localPathsCheckBox, ELProperty.create("${selected}"), localPathsList, BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        org.openide.awt.Mnemonics.setLocalizedText(localPathsAddButton, org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.localPathsAddButton.text")); // NOI18N
        localPathsAddButton.setName("localPathsAdd"); // NOI18N
        localPathsAddButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                localPathsAddButtonActionPerformed(evt);
            }
        });
        binding = createAutoBinding(AutoBinding.UpdateStrategy.READ_WRITE, localPathsCheckBox, ELProperty.create("${selected}"), localPathsAddButton, BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        org.openide.awt.Mnemonics.setLocalizedText(localPathsEditButton, org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.localPathsEditButton.text")); // NOI18N
        localPathsEditButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                localPathsEditButtonActionPerformed(evt);
            }
        });
        binding = createAutoBinding(AutoBinding.UpdateStrategy.READ_WRITE, localPathsCheckBox, ELProperty.create("${selected}"), localPathsEditButton, BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        localPathsDeleteButton.setText(org.openide.util.NbBundle.getMessage(ProductAccessPanel.class, "ProductAccessPanel.localPathsDeleteButton.text")); // NOI18N
        localPathsDeleteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                localPathsDeleteButtonActionPerformed(evt);
            }
        });
        binding = createAutoBinding(AutoBinding.UpdateStrategy.READ_WRITE, localPathsCheckBox, ELProperty.create("${selected}"), localPathsDeleteButton, BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                          .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                            .addComponent(defaultUrlRadioButton)
                                                            .addComponent(customClRadioButton))
                                          .addGap(0, 512, Short.MAX_VALUE))
                        .addGroup(layout.createSequentialGroup()
                                          .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                            .addGroup(layout.createSequentialGroup()
                                                                              .addGap(21, 21, 21)
                                                                              .addComponent(defaultUrlTextField))
                                                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                                                    .addContainerGap()
                                                                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                                      .addGroup(layout.createSequentialGroup()
                                                                                                        .addComponent(localPathsCheckBox)
                                                                                                        .addGap(400, 400, 400))
                                                                                      .addGroup(layout.createSequentialGroup()
                                                                                                        .addGap(21, 21, 21)
                                                                                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                                                                          .addGroup(layout.createSequentialGroup()
                                                                                                                                            .addComponent(workingDirLabel)
                                                                                                                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                                                                                            .addComponent(customClWorkingDirTextField))
                                                                                                                          .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING)
                                                                                                                          .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                                                                                                                  .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
                                                                                                                                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                                                                                  .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                                                                                                                                    .addComponent(localPathsAddButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                                                                                                    .addComponent(localPathsDeleteButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                                                                                                    .addComponent(localPathsEditButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))))))
                                          .addContainerGap())
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                          .addComponent(defaultUrlRadioButton)
                                          .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                          .addComponent(defaultUrlTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                          .addGap(12, 12, 12)
                                          .addComponent(customClRadioButton)
                                          .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                          .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
                                          .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                          .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                            .addComponent(workingDirLabel)
                                                            .addComponent(customClWorkingDirTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                          .addGap(11, 11, 11)
                                          .addComponent(localPathsCheckBox)
                                          .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                          .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                            .addGroup(layout.createSequentialGroup()
                                                                              .addComponent(localPathsAddButton)
                                                                              .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                              .addComponent(localPathsEditButton)
                                                                              .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                              .addComponent(localPathsDeleteButton)
                                                                              .addGap(0, 42, Short.MAX_VALUE))
                                                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                                          .addContainerGap())
        );

        bindingGroup.bind();
    }

    private void localPathsAddButtonActionPerformed(java.awt.event.ActionEvent evt) {
        EditLocalPathsDialog dialog = new EditLocalPathsDialog(SwingUtilities.getWindowAncestor(this), ProductAccessOptions.getPreferences());
        dialog.setSelectedPath("");
        dialog.setVisible(true);
        if (dialog.getReturnStatus() == EditLocalPathsDialog.RET_OK) {
            String localPathsValue = dialog.getSelectedPath();
            String[] newPaths = localPathsValue.split(File.pathSeparator);
            for (String newPath : newPaths) {
                newPath = newPath.trim();
                if (!localPathsListModel.contains(newPath)) {
                    localPathsListModel.addElement(newPath);
                }
            }
        }
    }

    private void localPathsEditButtonActionPerformed(java.awt.event.ActionEvent evt) {
        final int[] selectedIndices = localPathsList.getSelectedIndices();
        if (selectedIndices.length > 0) {
            String localPathsValue = IntStream.of(selectedIndices)
                    .mapToObj(localPathsListModel::getElementAt)
                    .collect(Collectors.joining(File.pathSeparator));
            EditLocalPathsDialog dialog = new EditLocalPathsDialog(SwingUtilities.windowForComponent(this), ProductAccessOptions.getPreferences());
            dialog.setSelectedPath(localPathsValue);
            dialog.setVisible(true);
            if (dialog.getReturnStatus() == EditLocalPathsDialog.RET_OK) {
                localPathsValue = dialog.getSelectedPath();
                String[] editedPaths = localPathsValue.split(File.pathSeparator);
                for (int i = 0; i < editedPaths.length; i++) {
                    String editedPath = editedPaths[i].trim();
                    int index = selectedIndices[i];
                    if (!editedPath.isEmpty() && index >= 0 && index < localPathsListModel.getSize()) {
                        localPathsListModel.set(index, editedPath);
                    }
                }
            }
        }
    }

    private void localPathsDeleteButtonActionPerformed(java.awt.event.ActionEvent evt) {
        final int[] selectedIndices = localPathsList.getSelectedIndices();
        Arrays.sort(selectedIndices);
        for (int i = selectedIndices.length - 1; i >= 0; --i) {
            int index = selectedIndices[i];
            localPathsListModel.remove(index);
        }
    }

    void load() {
        ProductAccessOptions options = ProductAccessOptions.getDefault();
        defaultUrlTextField.setText(options.getDefaultUrl());
        customClRadioButton.setSelected(options.getCustomCommandLineEnabled());
        customClCodeTextArea.setText(options.getCustomCommandLineCode());
        customClWorkingDirTextField.setText(options.getCustomCommandLineWorkingDir());
        localPathsCheckBox.setSelected(options.getLocalPathsEnabled());
        localPathsListModel.removeAllElements();
        for (String path : options.getLocalPaths()) {
            localPathsListModel.addElement(path);
        }
    }

    void store() {

        ProductAccessOptions options = ProductAccessOptions.getDefault();
        options.setDefaultUrl(defaultUrlTextField.getText());
        options.setCustomCommandLineEnabled(customClRadioButton.isSelected());
        options.setCustomCommandLineCode(customClCodeTextArea.getText());
        options.setCustomCommandLineWorkingDir(customClWorkingDirTextField.getText());
        options.setDefaultUrl(defaultUrlTextField.getText());
        options.setLocalPathsEnabled(localPathsCheckBox.isSelected());
        options.setLocalPaths(Stream.of(localPathsListModel.toArray()).map(Object::toString).toArray(String[]::new));

        try {
            ProductAccessOptions.getPreferences().flush();
        } catch (BackingStoreException e) {
            SnapApp.getDefault().getLogger().severe(e.getMessage());
        }
    }

    boolean valid() {
        // Check whether form is consistent and complete
        return true;
    }

}
