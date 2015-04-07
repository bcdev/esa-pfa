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

import org.esa.beam.framework.ui.application.support.AbstractToolView;
import org.esa.beam.visat.VisatApp;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.ordering.ProductOrder;
import org.esa.pfa.ordering.ProductOrderBasket;
import org.esa.pfa.search.CBIRSession;
import org.esa.pfa.search.Classifier;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

/**
 * Product Ordering Toolview
 */
public class CBIROrderingToolView extends AbstractToolView implements Patch.PatchListener, CBIRSession.Listener {

    public final static String ID = "org.esa.pfa.ui.toolviews.cbir.CBIROrderingToolView";

    private final CBIRSession session;

    private ProductOrderTableModel productListModel;
    private JTable table;
    private File localProductDir;

    public CBIROrderingToolView() {
        session = CBIRSession.getInstance();
        session.addListener(this);
    }

    public JComponent createControl() {

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        //actionPanel.add(new JButton("Start all"));
        //actionPanel.add(new JButton("Stop all"));

        DefaultTableColumnModel columnModel = new DefaultTableColumnModel();
        columnModel.addColumn(new TableColumn(0, 128));
        columnModel.addColumn(new TableColumn(1, 128, new StatusCellRenderer(), null));
        columnModel.getColumn(0).setHeaderValue("Product");
        columnModel.getColumn(1).setHeaderValue("Order Status");

        table = new JTable(productListModel, columnModel);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedProduct();
                }
            }
        });

        table.setRowHeight(table.getRowHeight() + 6);
        table.setGridColor(Color.GRAY);

        JPanel control = new JPanel(new BorderLayout(4, 4));
        control.setBorder(new EmptyBorder(4, 4, 4, 4));
        control.add(new JLabel("Data products ordered:"), BorderLayout.NORTH);
        control.add(new JScrollPane(table), BorderLayout.CENTER);
        control.add(actionPanel, BorderLayout.SOUTH);

        setProductOrderBasket(session.getProductOrderBasket());

        return control;
    }

    void setProductOrderBasket(ProductOrderBasket productOrderBasket) {
        productListModel = new ProductOrderTableModel(productOrderBasket);
        productOrderBasket.addListener(productListModel);
        if (table != null) {
            table.setModel(productListModel);
        }
    }

    void setLocalProductDir(File localProductDir) {
        this.localProductDir = localProductDir;
    }

    private void openSelectedProduct() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow != -1) {
            ProductOrder productOrder = productListModel.getProductOrderBasket().getProductOrder(selectedRow);
            if (localProductDir == null) {
                // config property not set?
                return;
            }

            final File parentProductFile = new File(localProductDir, productOrder.getProductName());
            if (!parentProductFile.exists()) {
                return;
            }

            try {
                PatchContextMenuFactory.openProduct(parentProductFile);
            } catch (Exception e1) {
                VisatApp.getApp().handleUnknownException(e1);
            }
        }
    }

    @Override
    public void notifyStateChanged(final Patch patch) {
    }

    @Override
    public void notifySessionMsg(final CBIRSession.Notification msg, final Classifier classifier) {
        switch (msg) {
            case NewClassifier:
                final PFAApplicationDescriptor applicationDescriptor = session.getApplicationDescriptor();
                setLocalProductDir(applicationDescriptor.getLocalProductDir());
                break;
            case DeleteClassifier:
                break;
            case NewTrainingImages:
                break;
            case ModelTrained:
                break;
        }
    }

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        JProgressBar progressBar;

        private StatusCellRenderer() {
            progressBar = new JProgressBar();
            progressBar.setMinimum(0);
            progressBar.setMaximum(100);
            progressBar.setStringPainted(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

            ProductOrder productOrder = (ProductOrder) value;
            if (productOrder != null) {
                int progress = productOrder.getProgress();
                if (productOrder.getState() == ProductOrder.State.DOWNLOADING) {
                    progressBar.setValue(progress);
                    progressBar.setString(progress + "% downloaded");
                    return progressBar;
                } else if (productOrder.getState() != null) {
                    String text = productOrder.getMessage() != null ? productOrder.getMessage() : productOrder.getState().toString();
                    super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column);
                    Font font = getFont();
                    setFont(new Font(font.getName(), Font.BOLD, font.getSize()));
                    setForeground(getStateColor(productOrder.getState()));
                    return this;
                }
            }
            super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
            return this;
        }

        Color getStateColor(ProductOrder.State state) {
            if (state == ProductOrder.State.COMPLETED) {
                return Color.GREEN.darker();
            } else if (state == ProductOrder.State.WAITING) {
                return Color.BLUE.darker();
            } else if (state == ProductOrder.State.SUBMITTED) {
                return Color.BLUE.darker();
            } else if (state == ProductOrder.State.ERROR) {
                return Color.RED.darker();
            } else {
                return Color.DARK_GRAY;
            }
        }
    }

    private static class ProductOrderTableModel extends AbstractTableModel implements ProductOrderBasket.Listener {
        private final ProductOrderBasket productOrderBasket;

        public ProductOrderTableModel(ProductOrderBasket productOrderBasket) {
            this.productOrderBasket = productOrderBasket;
            productOrderBasket.addListener(this);
        }

        public ProductOrderBasket getProductOrderBasket() {
            return productOrderBasket;
        }

        @Override
        public int getRowCount() {
            return productOrderBasket.getProductOrderCount();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ProductOrder productOrder = productOrderBasket.getProductOrder(rowIndex);
            if (columnIndex == 0) {
                return productOrder.getProductName();
            }
            return productOrder;
        }

        @Override
        public void basketChanged(ProductOrderBasket basket) {
            fireTableDataChanged();
        }

        @Override
        public void orderStateChanged(ProductOrderBasket basket, ProductOrder order) {
            int orderIndex = basket.getProductOrderIndex(order);
            if (orderIndex >= 0) {
                fireTableRowsUpdated(orderIndex, orderIndex);
            }
        }
    }
}
