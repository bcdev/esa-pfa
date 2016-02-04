package org.esa.pfa.gui.toolviews.support;

import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.rcp.util.Dialogs;

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
        boolean desktopSupported = Desktop.isDesktopSupported();
        boolean browseSupported = Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
        if (desktopSupported && browseSupported) {
            try {
                Desktop.getDesktop().browse(fexOverviewUri);
            } catch (Throwable t) {
                Dialogs.showError(String.format("Failed to open URL:\n%s:\n%s", fexOverviewUri, t.getMessage()));
            }
        } else {
            SystemUtils.copyToClipboard(fexOverviewUri.toString());
            Dialogs.showInformation("The URL has been copied to your Clipboard\n");
        }
    }
}
