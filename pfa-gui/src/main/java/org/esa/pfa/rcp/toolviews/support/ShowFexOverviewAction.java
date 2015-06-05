package org.esa.pfa.rcp.toolviews.support;

import org.esa.snap.rcp.SnapDialogs;

import javax.swing.AbstractAction;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.net.URI;

/**
 * @author Norman Fomferra
 */
class ShowFexOverviewAction extends AbstractAction {

    private final URI fexOverviewUri;

    public ShowFexOverviewAction(URI fexOverviewUri) {
        super("Show Fex Overview");
        this.fexOverviewUri = fexOverviewUri;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            Desktop.getDesktop().browse(fexOverviewUri);
        } catch (Throwable t) {
            SnapDialogs.showError("Failed to open URL:\n" + fexOverviewUri + ":\n" + t.getMessage());
        }
    }

}
