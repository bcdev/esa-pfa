package org.esa.pfa.fe.spectral;

import org.esa.snap.framework.datamodel.GeoPos;
import org.junit.Before;
import org.junit.Test;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.util.Random;

import static org.junit.Assert.assertEquals;


/**
 * Created by Norman on 07.07.2015.
 */
public class GlobalPatchCSTest {

    @Test
    public void testRrrr() throws Exception {
        int N = 1024 * 1024 / 8;
        Random random = new Random();
        DataOutputStream stream = new DataOutputStream(new FileOutputStream("M1.DAT"));
        for (int i = 0; i < N; i++) {
            stream.writeLong(random.nextLong());
        }
        stream.close();
    }

    @Test
    public void testGetLowerBoundGeoPos() throws Exception {
        GlobalPatchCS cs = new GlobalPatchCS(0.1);
        GeoPos result = cs.getLowerBoundGeoPos(new GeoPos(-1.24, +3.86),
                                               new GeoPos(-2.78, -2.02),
                                               new GeoPos(+3.91, -1.08),
                                               new GeoPos(+1.55, +3.98));
        assertEquals(-2.8, result.lat, 1e-10);
        assertEquals(-2.1, result.lon, 1e-10);
    }

    @Test
    public void testGetUpperBoundGeoPos() throws Exception {
        GlobalPatchCS cs = new GlobalPatchCS(0.1);
        GeoPos result = cs.getUpperBoundGeoPos(new GeoPos(-1.24, +3.86),
                                               new GeoPos(-2.78, -2.02),
                                               new GeoPos(+3.91, -1.08),
                                               new GeoPos(+1.55, +3.98));
        assertEquals(+4.0, result.lat, 1e-10);
        assertEquals(+4.0, result.lon, 1e-10);
    }

    @Test
    public void testLowerBoundCoordinate() throws Exception {
        GlobalPatchCS cs = new GlobalPatchCS(0.5);
        testLowerBoundCoordinate(cs, -1.0, -0.9);
        testLowerBoundCoordinate(cs, -0.5, -0.5);
        testLowerBoundCoordinate(cs, -0.5, -0.1);
        testLowerBoundCoordinate(cs, 0.0, 0.0);
        testLowerBoundCoordinate(cs, 0.0, 0.1);
        testLowerBoundCoordinate(cs, 0.5, 0.5);
        testLowerBoundCoordinate(cs, 0.5, 0.9);
        testLowerBoundCoordinate(cs, 1.0, 1.0);
        testLowerBoundCoordinate(cs, 1.0, 1.1);
        testLowerBoundCoordinate(cs, 1.5, 1.5);
        testLowerBoundCoordinate(cs, 1.5, 1.9);
    }

    @Test
    public void testUpperBoundCoordinate() throws Exception {
        GlobalPatchCS cs = new GlobalPatchCS(0.5);
        testUpperBoundCoordinate(cs, -0.5, -0.9);
        testUpperBoundCoordinate(cs, 0.0, -0.5);
        testUpperBoundCoordinate(cs, 0.0, -0.1);
        testUpperBoundCoordinate(cs, 0.5, 0.0);
        testUpperBoundCoordinate(cs, 0.5, 0.1);
        testUpperBoundCoordinate(cs, 1.0, 0.5);
        testUpperBoundCoordinate(cs, 1.0, 0.9);
        testUpperBoundCoordinate(cs, 1.5, 1.0);
        testUpperBoundCoordinate(cs, 1.5, 1.1);
        testUpperBoundCoordinate(cs, 2.0, 1.5);
        testUpperBoundCoordinate(cs, 2.0, 1.9);
    }

    private void testLowerBoundCoordinate(GlobalPatchCS cs, double expected, double value) {
        assertEquals(expected, cs.getLowerBoundGeoPos(value), 1e-10);
    }

    private void testUpperBoundCoordinate(GlobalPatchCS cs, double expected, double value) {
        assertEquals(expected, cs.getUpperBoundCoordinate(value), 1e-10);
    }
}
