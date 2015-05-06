/*
 * Copyright (C) 2013 by Array Systems Computing Inc. http://www.array.ca
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

import org.esa.pfa.fe.op.AttributeType;
import org.esa.pfa.fe.op.FeatureType;

import java.awt.*;
import java.util.ArrayList;
import java.util.Set;

/**

 */
public abstract class AbstractApplicationDescriptor implements PFAApplicationDescriptor {

    private final String name;
    private final String id;

    protected AbstractApplicationDescriptor(final String name, final String id) {
        this.name = name;
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public String getId() {
        return id;
    }

    /**
     * Gets the width and height of the patch segmentation.
     *
     * @return the  dimension
     */
    public abstract Dimension getPatchDimension();

    public static FeatureType[] getEffectiveFeatureTypes(FeatureType[] featureTypes, Set<String> featureNames) {
        ArrayList<FeatureType> effectiveFeatureTypes = new ArrayList<>();
        for (FeatureType featureType : featureTypes) {
            if (featureType.hasAttributes()) {
                for (AttributeType attrib : featureType.getAttributeTypes()) {
                    final String effectiveName = featureType.getName() + '.' + attrib.getName();
                    if (acceptFeatureTypeName(featureNames, effectiveName)) {
                        FeatureType newFeaType = new FeatureType(effectiveName, attrib.getDescription(), attrib.getValueType());
                        effectiveFeatureTypes.add(newFeaType);
                    }
                }
            } else {
                if (acceptFeatureTypeName(featureNames, featureType.getName())) {
                    effectiveFeatureTypes.add(featureType);
                }
            }
        }
        return effectiveFeatureTypes.toArray(new FeatureType[effectiveFeatureTypes.size()]);
    }

    private static boolean acceptFeatureTypeName(Set<String> allowedNames, String name) {
        return allowedNames == null || allowedNames.contains(name);
    }

}
