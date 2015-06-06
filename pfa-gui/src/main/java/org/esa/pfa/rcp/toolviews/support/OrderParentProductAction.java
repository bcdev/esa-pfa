package org.esa.pfa.rcp.toolviews.support;

import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.ordering.ProductOrder;
import org.esa.pfa.ordering.ProductOrderBasket;
import org.esa.pfa.ordering.ProductOrderService;
import org.esa.pfa.search.CBIRSession;
import org.esa.snap.rcp.SnapDialogs;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;

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

        ProductOrderBasket productOrderBasket = CBIRSession.getInstance().getProductOrderBasket();
        ProductOrder productOrder = productOrderBasket.getProductOrder(parentProductName);
        if (productOrder != null) {
            if (productOrder.getState() == ProductOrder.State.COMPLETED) {
                SnapDialogs.Answer resp = SnapDialogs.requestDecision((String) getValue(NAME),
                                                                      String.format("Data product\n%s\nhas already been downloaded.\nOpen it?",
                                                                                    parentProductName), true, null);
                if (resp == SnapDialogs.Answer.YES) {
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
