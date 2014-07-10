package org.esa.pfa.ui.toolviews.cbir;

import org.esa.pfa.search.CBIRSession;
import org.esa.pfa.search.SearchToolStub;

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
    private JComboBox<String> quickLookCombo;
    private JComboBox<ImageIcon> imageModeCombo;

    private static final ImageIcon iconSingle = new ImageIcon(OptionsControlPanel.class.getClassLoader().getResource("images/patch_single.png"));
    private static final ImageIcon iconDual = new ImageIcon(OptionsControlPanel.class.getClassLoader().getResource("images/patch_dual.png"));

    public static enum Notification { SET_ALL_RELEVANT, SET_ALL_IRRELEVANT, QUICKLOOK_CHANGED }
    private final List<Listener> listenerList = new ArrayList<>(1);

    private final CBIRSession session;

    public OptionsControlPanel(final CBIRSession session) {
        super(new FlowLayout(FlowLayout.RIGHT));
        this.session = session;

        createControl();
        showSetAllButtons(false);
    }

    private void createControl() {

        allRelevantBtn = new JButton("Set all relevant");
        allRelevantBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fireNotification(Notification.SET_ALL_RELEVANT);
            }
        });
        this.add(allRelevantBtn);
        allIrrelevantBtn = new JButton("Set all irrelevant");
        allIrrelevantBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fireNotification(Notification.SET_ALL_IRRELEVANT);
            }
        });
        this.add(allIrrelevantBtn);

        quickLookCombo = new JComboBox<>();
        quickLookCombo.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    session.setQuicklookBandName((String) quickLookCombo.getSelectedItem());

                    fireNotification(Notification.QUICKLOOK_CHANGED);
                }
            }
        });
        //this.add(new JLabel("Band shown:"));
        this.add(quickLookCombo);

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
        quickLookCombo.setEnabled(flag);
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
        if (quickLookCombo.getItemCount() == 0) {
            for (String bandName : bandNames) {
                quickLookCombo.addItem(bandName);
            }
            quickLookCombo.setSelectedItem(defaultBandName);
        }
    }

    public void clearData() {
        quickLookCombo.removeAllItems();
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
