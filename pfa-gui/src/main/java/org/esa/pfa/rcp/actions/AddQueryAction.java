/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.pfa.rcp.actions;

import com.bc.ceres.swing.figure.AbstractInteractorListener;
import com.bc.ceres.swing.figure.Interactor;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.search.CBIRSession;
import org.esa.pfa.rcp.toolviews.PatchProcessor;
import org.esa.pfa.rcp.toolviews.PatchSelectionInteractor;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.ProductNode;
import org.esa.snap.framework.ui.product.ProductSceneView;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.SnapDialogs;
import org.esa.snap.rcp.actions.interactors.InsertFigureInteractorInterceptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.ContextAwareAction;
import org.openide.util.HelpCtx;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.WeakListeners;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

@ActionID(
        category = "Tools",
        id = "AddQueryAction"
)
@ActionRegistration(
        displayName = "#CTL_AddQueryAction_MenuText",
        popupText = "#CTL_AddQueryAction_MenuText"
)
@ActionReferences({
        @ActionReference(
                path = "Toolbars/PFA",
                position = 500
        )
})
@NbBundle.Messages({
        "CTL_AddQueryAction_MenuText=Add Query",
        "CTL_AddQueryAction_ShortDescription=Add PFA query image"
})
public class AddQueryAction extends AbstractAction implements ContextAwareAction, LookupListener, HelpCtx.Provider {

    private static final String HELP_ID = "pfaQuery";
    private final Lookup lkp;

    public AddQueryAction() {
        this(Utilities.actionsGlobalContext());
    }

    public AddQueryAction(Lookup lkp) {
        super(Bundle.CTL_AddQueryAction_MenuText());
        this.lkp = lkp;
        Lookup.Result<ProductNode> lkpContext = lkp.lookupResult(ProductNode.class);
        lkpContext.addLookupListener(WeakListeners.create(LookupListener.class, this, lkpContext));
        setEnableState();
        putValue(Action.SHORT_DESCRIPTION, Bundle.CTL_AddQueryAction_ShortDescription());
        putValue(SMALL_ICON, ImageUtilities.loadImageIcon("images/icons/pfa-add-query-24.png", false));
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new AddQueryAction(actionContext);
    }

    @Override
    public void resultChanged(LookupEvent ev) {
        setEnableState();
    }

    private void setEnableState() {
        boolean state = CBIRSession.getInstance().hasClassifier() && SnapApp.getDefault().getSelectedProductSceneView() != null;
        setEnabled(state);
    }

    @Override
    public void actionPerformed(ActionEvent event) {

        final Dimension dim = CBIRSession.getInstance().getApplicationDescriptor().getPatchDimension();
        final PatchSelectionInteractor interactor = new PatchSelectionInteractor(dim.width, dim.height);
        interactor.addListener(new PatchInteractorListener());
        interactor.addListener(new InsertFigureInteractorInterceptor());
        interactor.activate();

        SnapApp.getDefault().getSelectedProductSceneView().getFigureEditor().setInteractor(interactor);
    }

    @Override
    public HelpCtx getHelpCtx() {
        return new HelpCtx(HELP_ID);
    }


    public class PatchInteractorListener extends AbstractInteractorListener {

        @Override
        public void interactionStarted(Interactor interactor, InputEvent inputEvent) {
        }

        @Override
        public void interactionStopped(Interactor interactor, InputEvent inputEvent) {
            if (!CBIRSession.getInstance().hasClassifier()) {
                return;
            }
            final PatchSelectionInteractor patchInteractor = (PatchSelectionInteractor) interactor;
            if (patchInteractor != null) {
                try {
                    Rectangle2D rect = patchInteractor.getPatchShape();

                    ProductSceneView productSceneView = getProductSceneView(inputEvent);
                    RenderedImage parentImage = productSceneView != null ? productSceneView.getBaseImageLayer().getImage() : null;

                    final Product product = SnapApp.getDefault().getSelectedProduct();
                    addQueryImage(product, (int) rect.getX(), (int) rect.getY(), (int) rect.getWidth(),
                                  (int) rect.getHeight(), parentImage, productSceneView);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void addQueryImage(final Product product, final int x, final int y, final int w, final int h,
                                   final RenderedImage parentImage, final ProductSceneView productSceneView) throws IOException {
            final Window parentWindow = SwingUtilities.getWindowAncestor(productSceneView);
            final Rectangle region = new Rectangle(x, y, w, h);
            final PatchProcessor patchProcessor = new PatchProcessor(parentWindow, product, parentImage, region, CBIRSession.getInstance());
            patchProcessor.executeWithBlocking();
            Patch patch = null;
            try {
                patch = patchProcessor.get();
            } catch (InterruptedException | ExecutionException e) {
                SnapApp.getDefault().handleError("Failed to extract patch", e);
            }
            if (patch != null && patch.getFeatureValues().length > 0) {
                CBIRSession.getInstance().addQueryPatch(patch);
                // session notifies listeners that a new query is added
            } else {
                SnapDialogs.showWarning("Failed to extract features for this patch");
            }
        }

        private ProductSceneView getProductSceneView(InputEvent event) {
            ProductSceneView productSceneView = null;
            Component component = event.getComponent();
            while (component != null) {
                if (component instanceof ProductSceneView) {
                    productSceneView = (ProductSceneView) component;
                    break;
                }
                component = component.getParent();
            }
            return productSceneView;
        }
    }
}
