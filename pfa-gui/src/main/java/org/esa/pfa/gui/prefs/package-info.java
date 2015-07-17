/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
@OptionsPanelController.ContainerRegistration(
        id = "ESA_PFA", 
        categoryName = "#LBL_PfaOptionsCategory_Name",
        iconBase = "org/esa/pfa/gui/prefs/pfa-logo-32.png",
        keywords = "#LBL_PfaOptionsCategory_Keywords",
        keywordsCategory = "ESA_PFA",
        position = 1000
)
@NbBundle.Messages(value = {
    "LBL_PfaOptionsCategory_Name=ESA PFA",
    "LBL_PfaOptionsCategory_Keywords=index,feature,pfa"
})
package org.esa.pfa.gui.prefs;

import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
