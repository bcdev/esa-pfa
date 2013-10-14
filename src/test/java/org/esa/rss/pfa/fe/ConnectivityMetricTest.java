package org.esa.rss.pfa.fe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Norman Fomferra
 */
public class ConnectivityMetricTest {
    @Test
    public void testCompute4x4() throws Exception {
        ConnectivityMetric cm = ConnectivityMetric.compute(4, 4, new byte[]{
                1, 1, 1, 1,
                1, 1, 1, 1,
                1, 1, 0, 0,
                1, 1, 0, 0,
        });

        assertEquals((4 + 4 + 2 + 2) / 4.0, cm.meanMaxSectionLengthH, 1e-5);

        assertEquals((4 + 4 + 2 + 2) / 4.0, cm.meanMaxSectionLengthV, 1e-5);

        assertEquals(0.75, cm.sectionLengthRatio, 1e-5);
    }

    @Test
    public void testCompute5x5() throws Exception {
        ConnectivityMetric cm = ConnectivityMetric.compute(5, 5, new byte[]{
                1, 0, 1, 1, 1,
                1, 1, 0, 1, 1,
                1, 0, 1, 0, 1,
                0, 1, 0, 1, 0,
                0, 1, 0, 1, 0,
        });

        assertEquals((3 + 2 + 1 + 1 + 1) / 5.0, cm.meanMaxSectionLengthH, 1e-5);

        assertEquals((3 + 2 + 1 + 2 + 3) / 5.0, cm.meanMaxSectionLengthV, 1e-5);

        assertEquals(0.38, cm.sectionLengthRatio, 1e-5);
    }

    @Test
    public void testCompute5x5_2() throws Exception {
        ConnectivityMetric cm = ConnectivityMetric.compute(5, 5, new byte[]{
                0, 1, 1, 1, 0,
                1, 1, 1, 1, 1,
                1, 1, 1, 1, 1,
                1, 1, 1, 1, 1,
                0, 1, 1, 1, 0,
        });

        assertEquals(4.2, cm.meanMaxSectionLengthH, 1e-5);

        assertEquals(4.2, cm.meanMaxSectionLengthV, 1e-5);

        assertEquals(0.84, cm.sectionLengthRatio, 1e-5);
    }

    @Test
    public void testCompute5x5_3() throws Exception {
        ConnectivityMetric cm = ConnectivityMetric.compute(5, 5, new byte[]{
                1, 1, 0, 0, 0,
                1, 1, 1, 1, 0,
                0, 1, 1, 1, 0,
                0, 1, 1, 1, 1,
                0, 0, 0, 1, 1,
        });

        assertEquals(3, cm.meanMaxSectionLengthH, 1e-5);

        assertEquals(3, cm.meanMaxSectionLengthV, 1e-5);

        assertEquals(0.6, cm.sectionLengthRatio, 1e-5);
    }
}
