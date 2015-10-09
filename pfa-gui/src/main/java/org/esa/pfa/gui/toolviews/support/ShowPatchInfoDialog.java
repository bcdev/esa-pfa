package org.esa.pfa.gui.toolviews.support;

import org.esa.pfa.fe.op.Feature;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.gui.search.CBIRSession;
import org.esa.snap.ui.ModelessDialog;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/**
 * @author Norman Fomferra
 */
class ShowPatchInfoDialog extends ModelessDialog {

    public ShowPatchInfoDialog(Window parent, Patch patch, JButton[] buttons) {
        super(parent, "Patch Info - " + patch.getPatchName(), ID_CLOSE, buttons, null);
        CBIRSession session = CBIRSession.getInstance();
        Object[][] array = getFeatureTableData(patch);
        JTable table = new JTable(new DefaultTableModel(array, new Object[]{"Name", "Value"}));

        JPanel contentPanel = new JPanel(new BorderLayout(2, 2));
        final String ql = session.getQuicklookBandName1();
        BufferedImage QlImage = patch.getImage(ql);
        if (QlImage != null) {

            JLabel imageCanvas = new JLabel(new ImageIcon(QlImage));
            imageCanvas.setBorder(new LineBorder(Color.DARK_GRAY));

            JPanel compRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
            compRow.add(imageCanvas);
            compRow.setPreferredSize(imageCanvas.getPreferredSize());

            contentPanel.add(compRow, BorderLayout.NORTH);
        }
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(200, 80));
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        setContent(contentPanel);
    }

    private Object[][] getFeatureTableData(Patch patch) {
        ArrayList<Object[]> data = new ArrayList<>();
        for (Feature feature : patch.getFeatures()) {
            data.add(new Object[]{feature.getName(), feature.getValue()});
        }
        return data.toArray(new Object[0][]);
    }

}
