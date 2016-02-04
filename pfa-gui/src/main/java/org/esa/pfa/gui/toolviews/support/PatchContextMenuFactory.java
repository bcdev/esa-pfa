package org.esa.pfa.gui.toolviews.support;

import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.gui.search.CBIRSession;
import org.esa.snap.core.util.Debug;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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
        if (patch.getParentProductName() == null) {
            return null;
        }

        final URI fexOverviewUri = CBIRSession.getInstance().getFexOverviewUri(patch);
        if (fexOverviewUri == null) {
            return null;
        }

        return new ShowFexOverviewAction(fexOverviewUri);
    }

    public Action createOrderParentProductAction(final Patch patch) {
        if (patch.getParentProductName() == null) {
            return null;
        }
        return new OrderParentProductAction(patch);
    }


    public Action createShowPatchInParentProductAction(final Patch patch) {
        if (patch.getParentProductName() == null) {
            return null;
        }

        PFAApplicationDescriptor applicationDescriptor = getSession().getApplicationDescriptor();
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
                patchProductFile = getSession().getPatchProductFile(patch);
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
