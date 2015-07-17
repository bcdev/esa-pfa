package org.esa.pfa.gui.toolviews.support;

import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.gui.search.CBIRSession;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.SnapDialogs;
import org.esa.snap.util.Debug;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import java.awt.event.ActionEvent;
import java.io.IOException;

/**
 * @author Norman Fomferra
 */
class ShowPatchInfoAction extends AbstractAction {
    private final Patch patch;

    ShowPatchInfoAction(Patch patch) {
        super("Show Patch Info");
        this.patch = patch;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        showPatchInfo();
    }

    private void showPatchInfo() {
        try {
            CBIRSession.getInstance().loadFeatures(patch);
            ShowPatchInfoDialog showPatchInfoDialog = new ShowPatchInfoDialog(SnapApp.getDefault().getMainFrame(), patch, createOtherButtons(patch));
            showPatchInfoDialog.getJDialog().setAlwaysOnTop(true);
            showPatchInfoDialog.show();
        } catch (IOException e) {
            Debug.trace(e);
            SnapDialogs.showError("Failed to load features for patch.", e.getMessage());
        }
    }

    private JButton[] createOtherButtons(Patch patch) {
        Action openParentProductAction = PatchContextMenuFactory.createShowPatchInParentProductAction(patch);
        if (openParentProductAction != null) {
            JButton button = new JButton(openParentProductAction);
            return new JButton[]{button};
        }
        return null;
    }
}
