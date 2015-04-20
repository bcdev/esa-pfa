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

import org.esa.pfa.fe.PFAApplicationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;


public class LocalClassifierServiceTest {

    private ClassifierService classifierService;
    private TestPFAApplicationDescriptor testPFAApplicationDescriptor;

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        URI testfolderUri = testFolder.getRoot().toURI();
        Path storagePath = Paths.get(testfolderUri);
        classifierService = new LocalClassifierService(storagePath);
        testPFAApplicationDescriptor = new TestPFAApplicationDescriptor();
        PFAApplicationRegistry.getInstance().addDescriptor(testPFAApplicationDescriptor);
    }

    @After
    public void tearDown() throws Exception {
        PFAApplicationRegistry.getInstance().removeDescriptor(testPFAApplicationDescriptor);
    }

    @Test
    public void testListEmpty() throws Exception {
        String[] list = classifierService.list();
        assertNotNull(list);
        assertEquals(0, list.length);
    }

    @Test
    public void testListSome() throws Exception {
        testFolder.newFile("foo.xml");
        testFolder.newFile("bar.xml");

        String[] list = classifierService.list();
        assertNotNull(list);
        assertEquals(2, list.length);
        assertThat(Arrays.asList(list), hasItems("foo", "bar"));
    }

    @Test
    public void testCreate() throws Exception {
        Classifier classifier = classifierService.create("cName", "testAppDesc");
        assertNotNull(classifier);
        assertEquals("cName", classifier.getName());
        assertSame(testPFAApplicationDescriptor, classifier.getApplicationDescriptor());

        String[] list = classifierService.list();
        assertNotNull(list);
        assertEquals(1, list.length);
        assertThat(Arrays.asList(list), hasItems("cName"));
    }

    @Test
    public void testDelete() throws Exception {
        testFolder.newFile("foo.xml");
        testFolder.newFile("bar.xml");

        classifierService.delete("foo");

        String[] list = classifierService.list();
        assertNotNull(list);
        assertEquals(1, list.length);
        assertThat(Arrays.asList(list), hasItems("bar"));
    }

    @Test
    public void testGetNotExist() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        classifierService.get("badName");
    }

    @Test
    public void testGetExisting() throws Exception {
        Classifier classifier1 = classifierService.create("cName", "testAppDesc");
        Classifier classifier2 = classifierService.get("cName");

        assertNotNull(classifier1);
        assertNotNull(classifier2);
        assertEquals("cName", classifier2.getName());
        assertSame(testPFAApplicationDescriptor, classifier2.getApplicationDescriptor());
    }

    @Test
    public void testSaveAutomatically() throws Exception {
        Classifier classifier1 = new Classifier("cName", testPFAApplicationDescriptor, classifierService);
        classifier1.setNumTrainingImages(99);
        classifier1.setNumRetrievedImages(199);

        Classifier classifier2 = classifierService.get("cName");
        assertNotNull(classifier2);
        assertEquals(99, classifier2.getNumTrainingImages());
        assertEquals(199, classifier2.getNumRetrievedImages());
    }
}