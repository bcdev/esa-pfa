package org.esa.pfa.gui.toolviews.support;

import org.esa.pfa.classifier.Classifier;
import org.esa.pfa.gui.search.CBIRSession;
import org.esa.snap.rcp.SnapApp;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Common controls for patch presentation
 */
public class OptionsControlPanel extends JPanel implements CBIRSession.Listener{

    private JLabel instructionLabel;
    private JButton allRelevantBtn, allIrrelevantBtn;
    private JButton cycleButton;
    private JComboBox<String> quickLookCombo1;
    private JComboBox<String> quickLookCombo2;
    private JComboBox<ImageIcon> imageModeCombo;

    private static final ImageIcon iconSingle = new ImageIcon(OptionsControlPanel.class.getClassLoader().getResource("images/patch_single.png"));
    private static final ImageIcon iconDual = new ImageIcon(OptionsControlPanel.class.getClassLoader().getResource("images/patch_dual.png"));
    private static final ImageIcon cycle = new ImageIcon(OptionsControlPanel.class.getClassLoader().getResource("images/cycle.png"));

    public enum Notification { SET_ALL_RELEVANT, SET_ALL_IRRELEVANT, QUICKLOOK_CHANGED }
    private final List<Listener> listenerList = new ArrayList<>(1);

    private final CBIRSession session;
    private String[] bandNames;

    public final static String USE_CONTROL_CENTRE_INSTRUCTION = "<html>"
            + "Use the PFA Control Centre "+
            "<img src=\""+ OptionsControlPanel.class.getClassLoader().getResource("images/icons/pfa-control-24.png")+ "\">"+
            " to connect to a database.</html>";
    public final static String USE_ADD_QUERY_INSTRUCTION = "<html>"
            + "Open a raster image and use the Add Query Image "+
            "<img src=\""+ OptionsControlPanel.class.getClassLoader().getResource("images/icons/pfa-add-query-24.png")+ "\"> tool.</html>";

    public OptionsControlPanel(final CBIRSession session) {
        super(new BorderLayout());
        this.session = session;

        createControl();
        showSetAllButtons(false);
        updateControls();
        this.session.addListener(this);
    }

    private void createControl() {

        final JPanel instructionPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 10));
        instructionLabel = new JLabel();
        instructionPanel.add(instructionLabel);
        this.add(instructionPanel, BorderLayout.WEST);

        final JPanel patchControlPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 0, 0));
        this.add(patchControlPanel, BorderLayout.EAST);

        allRelevantBtn = new JButton("Set all relevant ");
        allRelevantBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fireNotification(Notification.SET_ALL_RELEVANT);
            }
        });
        allIrrelevantBtn = new JButton("Set all irrelevant");
        allIrrelevantBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fireNotification(Notification.SET_ALL_IRRELEVANT);
            }
        });

        final JPanel setAllPanel = new JPanel();
        final BoxLayout setAlllayout = new BoxLayout(setAllPanel, BoxLayout.Y_AXIS);
        setAllPanel.setLayout(setAlllayout);
        setAllPanel.add(allRelevantBtn);
        setAllPanel.add(allIrrelevantBtn);
        patchControlPanel.add(setAllPanel);

        cycleButton = new JButton(cycle);
        cycleButton.setToolTipText("Cycle through the quicklooks");
        cycleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final String currentName = (String) quickLookCombo1.getSelectedItem();
                if(currentName != null) {
                    for (int i = 0; i < bandNames.length; ++i) {
                        if (currentName.equals(bandNames[i])) {
                            String newName;
                            if (i + 1 < bandNames.length) {
                                newName = bandNames[i + 1];
                            } else {
                                newName = bandNames[0];
                            }
                            quickLookCombo1.setSelectedItem(newName);
                            break;
                        }
                    }
                }
            }
        });
        patchControlPanel.add(cycleButton);

        final JPanel ql1Panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        quickLookCombo1 = new JComboBox<>();
        final ListCellRenderer<String> qlRenderer = new QlRenderer();
        quickLookCombo1.setRenderer(qlRenderer);
        quickLookCombo1.setToolTipText("Quicklook 1");
        quickLookCombo1.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    session.setQuicklookBandName1((String) quickLookCombo1.getSelectedItem());

                    fireNotification(Notification.QUICKLOOK_CHANGED);
                }
            }
        });
        ql1Panel.add(new JLabel("1)"));
        ql1Panel.add(quickLookCombo1);

        final JPanel ql2Panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        quickLookCombo2 = new JComboBox<>();
        quickLookCombo2.setRenderer(qlRenderer);
        quickLookCombo1.setToolTipText("Quicklook 2");
        quickLookCombo2.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    session.setQuicklookBandName2((String) quickLookCombo2.getSelectedItem());

                    fireNotification(Notification.QUICKLOOK_CHANGED);
                }
            }
        });
        ql2Panel.add(new JLabel("2)"));
        ql2Panel.add(quickLookCombo2);

        final JPanel qlListPanel = new JPanel();
        final BoxLayout qlListlayout = new BoxLayout(qlListPanel, BoxLayout.Y_AXIS);
        qlListPanel.setLayout(qlListlayout);
        qlListPanel.add(ql1Panel);
        qlListPanel.add(ql2Panel);
        patchControlPanel.add(qlListPanel);

        final ImageIcon[] items = { iconSingle, iconDual };
        imageModeCombo = new JComboBox<>(items);
        imageModeCombo.setToolTipText("Switch between single view or dual view");
        imageModeCombo.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    if(imageModeCombo.getSelectedItem().equals(iconSingle)) {
                        session.setImageMode(CBIRSession.ImageMode.SINGLE);
                    } else if(imageModeCombo.getSelectedItem().equals(iconDual)) {
                        session.setImageMode(CBIRSession.ImageMode.DUAL);
                    }

                    fireNotification(Notification.QUICKLOOK_CHANGED);
                }
            }
        });
        patchControlPanel.add(imageModeCombo);
    }

    /**
     * Enable or disable controls
     * @param flag true or false
     */
    private void setComponentEnabled(boolean flag) {
        cycleButton.setEnabled(flag);
        quickLookCombo1.setEnabled(flag);
        quickLookCombo2.setEnabled(flag && session.getImageMode() == CBIRSession.ImageMode.DUAL);
        imageModeCombo.setEnabled(flag);
    }

    /**
     * Show or hide the Set All Relevant and Irrelevant buttons
     * @param flag true or false
     */
    public void showSetAllButtons(final boolean flag) {
        allRelevantBtn.setVisible(flag);
        allIrrelevantBtn.setVisible(flag);
    }

    public void setInstructionTest(final String text) {
         instructionLabel.setText(text);
    }

    /**
     * Initial population of the quicklook list
     * @param bandNames quicklook band names
     * @param defaultBandName default name
     */
    private void populateQuicklookList(final String[] bandNames, final String defaultBandName) {
        if (quickLookCombo1.getItemCount() == 0) {
            this.bandNames = bandNames;
            for (String bandName : bandNames) {
                quickLookCombo1.addItem(bandName);
                quickLookCombo2.addItem(bandName);
            }
            quickLookCombo1.setSelectedItem(defaultBandName);
            quickLookCombo2.setSelectedItem(defaultBandName);
        }
    }

    public void clearData() {
        quickLookCombo1.removeAllItems();
        quickLookCombo2.removeAllItems();
    }

    public void addListener(final Listener listener) {
        if (!listenerList.contains(listener)) {
            listenerList.add(listener);
        }
    }

    private void fireNotification(final Notification msg) {
        for (Listener listener : listenerList) {
            listener.notifyOptionsMsg(msg);
        }
    }

    public interface Listener {
        void notifyOptionsMsg(final Notification msg);
    }

    private class QlRenderer extends BasicComboBoxRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            if(value != null) {
                String text = value.toString();
                if (text.endsWith("_ql.png")) {
                    text = text.substring(0, text.length() - 7);
                }
                setText(text);
            }
            return this;
        }
    }

    private void updateControls() {
        try {
            boolean hasClassifier = session.hasClassifier();
            setComponentEnabled(hasClassifier);

            if (!hasClassifier) {
                setInstructionTest(USE_CONTROL_CENTRE_INSTRUCTION);

                clearData();
            } else {
                if(instructionLabel.getText().equals(USE_CONTROL_CENTRE_INSTRUCTION)) {
                    setInstructionTest("");
                }

                CBIRSession.ImageMode imageMode = session.getImageMode();
                if (CBIRSession.ImageMode.SINGLE == imageMode) {
                    imageModeCombo.setSelectedIndex(0);
                } else {
                    imageModeCombo.setSelectedIndex(1);
                }

                String[] quicklookFileNames = session.getApplicationDescriptor().getQuicklookFileNames();
                if (!Objects.deepEquals(bandNames, quicklookFileNames)) {
                    clearData();
                    populateQuicklookList(quicklookFileNames,
                                          session.getApplicationDescriptor().getDefaultQuicklookFileName());
                } else {
                    quickLookCombo1.setSelectedItem(session.getQuicklookBandName1());
                    quickLookCombo2.setSelectedItem(session.getQuicklookBandName2());
                }
            }
        } catch (Exception e) {
            SnapApp.getDefault().handleError("Error updating controls", e);
        }
    }

    @Override
    public void notifySessionMsg(final CBIRSession.Notification msg, final Classifier classifier) {
        updateControls();
    }
}
