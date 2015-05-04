/*
 * Copyright (C) 2015 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.pfa.activelearning;

import libsvm.svm_model;

/**
 * Holds a reference to the svm_model
 */
public class SvmModelReference {
    private svm_model svmModel;

    public SvmModelReference() {
        this.svmModel = new svm_model();
    }

    public svm_model getSvmModel() {
        return svmModel;
    }

    public void setSvmModel(svm_model svmModel) {
        this.svmModel = svmModel;
    }
}
