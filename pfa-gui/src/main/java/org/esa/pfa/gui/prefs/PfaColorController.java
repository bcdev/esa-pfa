/*
 * Copyright (C) 2016 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.pfa.gui.prefs;

import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertySet;
import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.binding.BindingContext;
import org.esa.snap.rcp.preferences.DefaultConfigController;
import org.esa.snap.rcp.preferences.Preference;
import org.esa.snap.rcp.preferences.PreferenceUtils;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;

/**
 * Panel handling color preferences. Sub-panel of the "PFA"-panel.
 *
 * @author marcoz
 */
@OptionsPanelController.SubRegistration(
        location = "ESA_PFA",
        displayName = "Display",
        keywords = "pfa",
        keywordsCategory = "ESA PFA/Display",
        id = "PfaDisplay"
)
public final class PfaColorController extends DefaultConfigController {

    public static final String PREFERENCE_KEY_PATCH_BACKGROUND_COLOR = "pfa.patch.background.color";
    public static final Color DEFAULT_PATCH_BACKGROUND_COLOR = UIManager.getColor("Panel.background");
    public static final String PREFERENCE_KEY_PATCH_BORDER_COLOR = "pfa.patch.border.color";
    public static final Color DEFAULT_PATCH_BORDER_COLOR = Color.DARK_GRAY;

    protected PropertySet createPropertySet() {
        return createPropertySet(new PfaColorBean());
    }

    @Override
    protected JPanel createPanel(BindingContext context) {
        TableLayout tableLayout = new TableLayout(2);
        tableLayout.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        tableLayout.setTablePadding(new Insets(4, 10, 0, 0));
        tableLayout.setTableFill(TableLayout.Fill.BOTH);
        tableLayout.setColumnWeightX(1, 1.0);

        JPanel pageUI = new JPanel(tableLayout);

        JComponent[] bgColorComponents = getColorComponents(context, PREFERENCE_KEY_PATCH_BACKGROUND_COLOR);
        JComponent[] borderColorComponents = getColorComponents(context, PREFERENCE_KEY_PATCH_BORDER_COLOR);

        pageUI.add(bgColorComponents[0]);
        pageUI.add(bgColorComponents[1]);
        pageUI.add(borderColorComponents[0]);
        pageUI.add(borderColorComponents[1]);
        pageUI.add(tableLayout.createVerticalSpacer());

        JPanel parent = new JPanel(new BorderLayout());
        parent.add(pageUI, BorderLayout.CENTER);
        parent.add(Box.createHorizontalStrut(100), BorderLayout.EAST);
        return parent;
    }

    private static JComponent[] getColorComponents(BindingContext context, String key) {
        Property bgColor = context.getPropertySet().getProperty(key);
        return PreferenceUtils.createColorComponents(bgColor);
    }

    @Override
    public HelpCtx getHelpCtx() {
        return null;
    }

    @SuppressWarnings("UnusedDeclaration")
    static class PfaColorBean {

        @SuppressWarnings("AccessStaticViaInstance")
        @Preference(label = "Patch background colour",
                key = PREFERENCE_KEY_PATCH_BACKGROUND_COLOR)
        Color pfaBackgroundColor = DEFAULT_PATCH_BACKGROUND_COLOR;

        @Preference(label = "Patch border colour for dual view",
                key = PREFERENCE_KEY_PATCH_BORDER_COLOR,
                interval = "[0.0,1.0]")
        Color pfaBorderColor = DEFAULT_PATCH_BORDER_COLOR;
    }

}
