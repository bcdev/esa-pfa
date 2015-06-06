package org.esa.pfa.rcp.toolviews.support;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.search.CBIRSession;
import org.esa.snap.framework.dataio.ProductIO;
import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.ui.product.ProductSceneView;
import org.esa.snap.netbeans.docwin.WindowUtilities;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.SnapDialogs;
import org.esa.snap.rcp.actions.view.OpenImageViewAction;
import org.esa.snap.rcp.windows.ProductSceneViewTopComponent;
import org.esa.snap.util.Debug;
import org.esa.snap.util.ProductUtils;

import javax.swing.Action;
import javax.swing.JPopupMenu;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * @author Norman Fomferra
 */
public class PatchContextMenuFactory {

    private final CBIRSession session;

    public PatchContextMenuFactory(CBIRSession session) {
        this.session = session;
    }

    protected CBIRSession getSession() {
        return session;
    }

    public JPopupMenu createContextMenu(final Patch patch) {
        List<Action> actionList = getContextActions(patch);
        if (actionList.isEmpty()) {
            return null;
        }
        JPopupMenu popupMenu = new JPopupMenu();
        actionList.forEach(popupMenu::add);
        return popupMenu;
    }

    public List<Action> getContextActions(final Patch patch) {
        List<Action> actionList = new ArrayList<>();

        Action showPatchInfoAction = createShowPatchInfoAction(patch);
        if (showPatchInfoAction != null) {
            actionList.add(showPatchInfoAction);
        }

        Action openPatchProductAction = createOpenPatchProductAction(patch);
        if (openPatchProductAction != null) {
            actionList.add(openPatchProductAction);
        }

        Action openParentProductAction = createShowPatchInParentProductAction(patch);
        if (openParentProductAction != null) {
            actionList.add(openParentProductAction);
        }

        Action orderParentProductAction = createOrderParentProductAction(patch);
        if (orderParentProductAction != null) {
            actionList.add(orderParentProductAction);
        }

        Action showFexOverviewAction = createShowFexOverviewAction(patch);
        if (showFexOverviewAction != null) {
            actionList.add(showFexOverviewAction);
        }

        return actionList;
    }

    private ShowPatchInfoAction createShowPatchInfoAction(Patch patch) {
        if (patch.getFeatureValues().length == 0) {
            return null;
        }
        return new ShowPatchInfoAction(patch);
    }

    private Action createShowFexOverviewAction(Patch patch) {

        final String parentProductName = patch.getParentProductName();
        final boolean desktopSupported = Desktop.isDesktopSupported();
        final boolean browseSupported = Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);

        if (parentProductName == null || !desktopSupported || !browseSupported) {
            return null;
        }

        final URI fexOverviewUri = CBIRSession.getInstance().getFexOverviewUri(patch);
        if (fexOverviewUri == null) {
            return null;
        }

        return new ShowFexOverviewAction(fexOverviewUri);
    }

    public Action createOrderParentProductAction(final Patch patch) {
        final String parentProductName = patch.getParentProductName();
        if (parentProductName == null) {
            return null;
        }

        return new OrderParentProductAction(patch);
    }


    public static Action createShowPatchInParentProductAction(final Patch patch) {

        String parentProductName = patch.getParentProductName();
        if (parentProductName == null) {
            return null;
        }

        // O-oh, no good design here...
        CBIRSession session = CBIRSession.getInstance();
        PFAApplicationDescriptor applicationDescriptor = session.getApplicationDescriptor();
        if (applicationDescriptor == null) {
            // session not init?
            return null;
        }

        return new ShowPatchInProductAction("Show Patch in Parent Product", patch, null);
    }

    public Action createOpenPatchProductAction(final Patch patch) {
        File patchProductFile = null;
        if (getSession().hasClassifier()) {
            try {
                patchProductFile = getSession().getClassifier().getPatchProductFile(patch);
            } catch (IOException ignore) {
                Debug.trace(ignore);
            }
        }

        if (patchProductFile == null || !patchProductFile.exists()) {
            return null;
        }

        return new ShowPatchInProductAction("Open Patch Product", patch, patchProductFile);
    }


}
