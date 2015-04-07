package org.esa.pfa.ui.toolviews.cbir;

import org.esa.pfa.search.CBIRSession;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

/**
 * Common controls for patch presentation
 */
public class OptionsControlPanel extends JPanel {

    private JButton allRelevantBtn, allIrrelevantBtn;
    private JButton cycleButton;
    private JComboBox<String> quickLookCombo1;
    private JComboBox<String> quickLookCombo2;
    private JComboBox<ImageIcon> imageModeCombo;

    private static final ImageIcon iconSingle = new ImageIcon(OptionsControlPanel.class.getClassLoader().getResource("images/patch_single.png"));
    private static final ImageIcon iconDual = new ImageIcon(OptionsControlPanel.class.getClassLoader().getResource("images/patch_dual.png"));
    private static final ImageIcon cycle = new ImageIcon(OptionsControlPanel.class.getClassLoader().getResource("images/cycle.png"));

    public static enum Notification { SET_ALL_RELEVANT, SET_ALL_IRRELEVANT, QUICKLOOK_CHANGED }
    private final List<Listener> listenerList = new ArrayList<>(1);

    private final CBIRSession session;
    private String[] bandNames;

    public OptionsControlPanel(final CBIRSession session) {
        super(new FlowLayout(FlowLayout.TRAILING, 0, 0));
        this.session = session;

        createControl();
        showSetAllButtons(false);
    }

    private void createControl() {

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
        this.add(setAllPanel);

        cycleButton = new JButton(cycle);
        cycleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final String currentName = (String) quickLookCombo1.getSelectedItem();
                for(int i=0; i < bandNames.length; ++i) {
                    if(currentName.equals(bandNames[i])) {
                        String newName;
                        if(i+1 < bandNames.length) {
                            newName = bandNames[i+1];
                        } else {
                            newName = bandNames[0];
                        }
                        quickLookCombo1.setSelectedItem(newName);
                        break;
                    }
                }
            }
        });
        this.add(cycleButton);

        final JPanel ql1Panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        quickLookCombo1 = new JComboBox<>();
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
        this.add(qlListPanel);

        final ImageIcon[] items = { iconSingle, iconDual };
        imageModeCombo = new JComboBox<>(items);
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
        this.add(imageModeCombo);
    }

    /**
     * Enable or disable controls
     * @param flag true or false
     */
    public void setEnabled(final boolean flag) {
        cycleButton.setEnabled(flag);
        quickLookCombo1.setEnabled(flag);
        quickLookCombo2.setEnabled(flag);
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

    /**
     * Initial population of the quicklook list
     * @param bandNames quicklook band names
     * @param defaultBandName default name
     */
    public void populateQuicklookList(final String[] bandNames, final String defaultBandName) {
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
}
