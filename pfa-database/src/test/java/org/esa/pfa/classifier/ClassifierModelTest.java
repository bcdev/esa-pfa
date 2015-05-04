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

package org.esa.pfa.classifier;

import org.esa.pfa.fe.op.Patch;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ClassifierModelTest {

    @Test
    public void writeReadCycleSimpleProperties() throws Exception {
        ClassifierModel model1 = new ClassifierModel("app1");
        model1.setNumIterations(31);
        model1.setNumRetrievedImages(32);
        model1.setNumTrainingImages(33);
        model1.getSvmModelReference().getSvmModel().probA = new double[] {1,2,3};

        String xml = model1.toXML();
        System.out.println(xml);
        ClassifierModel model2 = ClassifierModel.fromXML(xml);

        assertEquals("app1", model2.getApplicationName());
        assertEquals(31, model2.getNumIterations());
        assertEquals(32, model2.getNumRetrievedImages());
        assertEquals(33, model2.getNumTrainingImages());

        assertNotNull(model2.getSvmModelReference().getSvmModel());
        assertArrayEquals(new double[]{1, 2, 3}, model2.getSvmModelReference().getSvmModel().probA, 1E-5);

        assertNotNull(model2.getQueryData());
        assertNotNull(model2.getTestData());
        assertNotNull(model2.getTrainingData());
        assertEquals(0, model2.getQueryData().size());
        assertEquals(0, model2.getTestData().size());
        assertEquals(0, model2.getTrainingData().size());

    }

    @Test
    public void writeReadCyclePatches() throws Exception {
        Patch p1 = new Patch("pName", 1, 2);
        Patch p2 = new Patch("pName", 1, 2);
        ClassifierModel model1 = new ClassifierModel("app1");
        model1.getQueryData().add(p1);
        model1.getQueryData().add(p2);

        String xml = model1.toXML();

//        System.out.println(xml);
        ClassifierModel model2 = ClassifierModel.fromXML(xml);

        List<Patch> patchInfo = model2.getQueryData();
        assertNotNull(patchInfo);
        assertEquals(2, patchInfo.size());

    }
}