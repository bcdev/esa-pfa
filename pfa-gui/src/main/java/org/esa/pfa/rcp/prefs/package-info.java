/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
@OptionsPanelController.ContainerRegistration(
        id = "ESA_PFA", 
        categoryName = "#OptionsCategory_Name_PFA", 
        iconBase = "org/esa/pfa/rcp/prefs/pfa-logo-32.png",
        keywords = "#OptionsCategory_Keywords_PFA", 
        keywordsCategory = "ESA_PFA",
        position = 1000
)
@NbBundle.Messages(value = {
    "OptionsCategory_Name_PFA=ESA PFA", 
    "OptionsCategory_Keywords_PFA=index,feature,pfa"
})
package org.esa.pfa.rcp.prefs;

import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
