package org.esa.pfa.gui.toolviews.support;

import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.gui.ordering.ProductAccessOptions;
import org.esa.pfa.gui.ordering.ProductAccessUtils;
import org.esa.pfa.gui.ordering.ProductOrder;
import org.esa.pfa.gui.ordering.ProductOrderBasket;
import org.esa.pfa.gui.ordering.ProductOrderService;
import org.esa.pfa.gui.search.CBIRSession;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.rcp.SnapDialogs;

import javax.swing.AbstractAction;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Norman Fomferra
 */
class OrderParentProductAction extends AbstractAction {

    private final Patch patch;

    public OrderParentProductAction(Patch patch) {
        super("Order Parent Product");
        this.patch = patch;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        orderParentProduct(patch);
    }

    private void orderParentProduct(Patch patch) {

        final String parentProductName = patch.getParentProductName();


        Product openedProduct = ProductAccessUtils.findOpenedProduct(parentProductName);
        if (openedProduct != null) {
            SnapDialogs.showInformation(String.format("Product '%s' is already opened.", parentProductName), null);
            return;
        }

        File productFile = ProductAccessUtils.findLocalFile(parentProductName, true, false);
        if (productFile != null) {
            SnapDialogs.Answer answer = SnapDialogs.requestDecision("Local File Found",
                                                                    String.format("A product named '%s' was found in your local path.\nDo you wish to open it?", parentProductName), true, null);
            if (answer == SnapDialogs.Answer.YES) {
                OpenProductAction.openProduct(productFile);
                return;
            } else if (answer == SnapDialogs.Answer.CANCELLED) {
                return;
            }
        }

        PFAApplicationDescriptor.ProductNameResolver productNameResolver = CBIRSession.getInstance().getApplicationDescriptor().getProductNameResolver();
        String defaultDataAccessPattern = ProductAccessOptions.getDefault().getDefaultUrl();
        if (productNameResolver != null && defaultDataAccessPattern != null) {
            String resolvedUrlString = productNameResolver.resolve(defaultDataAccessPattern, parentProductName);
            SystemUtils.LOG.info("Getting product " + resolvedUrlString);
            try {
                URI uri = new URI(resolvedUrlString);
                Desktop.getDesktop().browse(uri);
                // Obviously this action succeeded
                return;
            } catch (URISyntaxException | IOException e) {
                SnapDialogs.showError(e.getMessage());
            }
        }

        ////////////////////////////////////////////////////////////////////////////////////////
        // The following code places fake orders

        ProductOrderBasket productOrderBasket = CBIRSession.getInstance().getProductOrderBasket();
        ProductOrder productOrder = productOrderBasket.getProductOrder(parentProductName);
        if (productOrder != null) {
            if (productOrder.getState() == ProductOrder.State.COMPLETED) {
                SnapDialogs.Answer answer = SnapDialogs.requestDecision((String) getValue(NAME),
                                                                      String.format("Data product\n%s\nhas already been downloaded.\nOpen it?",
                                                                                    parentProductName), true, null);
                if (answer == SnapDialogs.Answer.YES) {
                    PatchContextMenuFactory.createShowPatchInParentProductAction(patch);
                }
            } else {
                SnapDialogs.showInformation((String) getValue(NAME),
                                            String.format("Data product\n%s\nis already in the basket.",
                                                          parentProductName), null);
            }
            return;
        }

        SnapDialogs.Answer resp = SnapDialogs.requestDecision((String) getValue(NAME),
                                                              String.format("Data product\n%s\nwill be ordered.\nProceed?",
                                                                            parentProductName), true, null);
        if (resp == SnapDialogs.Answer.YES) {
            ProductOrderService productOrderService = CBIRSession.getInstance().getProductOrderService();
            productOrderService.submit(new ProductOrder(parentProductName));
        }
    }
}
