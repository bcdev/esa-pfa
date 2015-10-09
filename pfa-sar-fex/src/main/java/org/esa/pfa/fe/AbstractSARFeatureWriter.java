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
package org.esa.pfa.fe;

import org.esa.pfa.fe.op.FeatureWriter;
import org.esa.pfa.fe.op.Patch;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.util.SystemUtils;

/**
 * Base class for SAR Feature Writers
 */
public abstract class AbstractSARFeatureWriter extends FeatureWriter {

    protected static boolean isPatchTooSmall(final Patch patch, final int numPixelsRequired) {
        final int patchX = patch.getPatchX();
        final int patchY = patch.getPatchY();
        final Product patchProduct = patch.getPatchProduct();

        final int numPixelsTotal = patchProduct.getSceneRasterWidth() * patchProduct.getSceneRasterHeight();

        final double patchPixelRatio = numPixelsTotal / (double) numPixelsRequired;
        if (patchPixelRatio < 0.6) {
            SystemUtils.LOG.warning(String.format("Rejected patch x%dy%d, patchPixelRatio=%f%%", patchX, patchY,
                                                  patchPixelRatio * 100));
            return true;
        }
        return false;
    }

    protected static Band getFeatureBand(final Product product, final String idStr) throws OperatorException {
        for(String name : product.getBandNames()) {
            if(name.contains(idStr)) {
                return product.getBand(name);
            }
        }
        throw new OperatorException(idStr +" band not found");
    }

    protected static Band getFeatureMask(final Product product, final String idStr) throws OperatorException {
        final String[] names = product.getMaskGroup().getNodeNames();
        for(String name : names) {
            if(name.contains(idStr)) {
                return product.getMaskGroup().get(name);
            }
        }
        throw new OperatorException(idStr +" mask not found");
    }
}
