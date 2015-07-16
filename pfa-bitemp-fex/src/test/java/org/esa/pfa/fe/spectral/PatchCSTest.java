package org.esa.pfa.fe.spectral;

import org.esa.snap.framework.datamodel.GeoPos;
import org.esa.snap.framework.datamodel.PixelPos;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.TiePointGeoCoding;
import org.esa.snap.framework.datamodel.TiePointGrid;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


/**
 * @author Norman
 */
public class PatchCSTest {

    @Test
    public void testPatchIndex() throws Exception {
        PatchCS patchCS = new PatchCS(200, 0.009);

        patchCS.getPatchIndex(90, -180);

    }

    @Test
    public void testReprojectedProduct() throws Exception {

        int sourceWidth = 1000;
        int sourceHeight = 2000;
        Product source = new Product("P", "T", sourceWidth, sourceHeight);
        TiePointGrid latTPG = new TiePointGrid("lat", 2, 2, 0, 0, sourceWidth, sourceHeight, new float[]{30.0f, 30.0f, 10.0f, 10.0f});
        TiePointGrid lonTPG = new TiePointGrid("lon", 2, 2, 0, 0, sourceWidth, sourceHeight, new float[]{40.0f, 50.0f, 40.0f, 50.0f});
        source.addTiePointGrid(latTPG);
        source.addTiePointGrid(lonTPG);
        source.addBand("reflec_8", "2.3");
        source.setGeoCoding(new TiePointGeoCoding(latTPG, lonTPG));

        double pixelRes = 0.009; // = 360 / 40000
        int patchSize = 200;

        Product target = new PatchCS(patchSize, pixelRes).getReprojectedProduct(source);
        assertEquals("X0022Y0017", target.getName());
        assertNotNull(target.getBand("lat"));
        assertNotNull(target.getBand("lon"));
        assertNotNull(target.getBand("reflec_8"));

        int targetWidth = target.getSceneRasterWidth();
        int targetHeight = target.getSceneRasterHeight();
        assertEquals(1200, targetWidth);
        assertEquals(2400, targetHeight);

        testGeoCoding(target, 0.0, 0.0, 39.6, 30.6);
        testGeoCoding(target, patchSize, 0.0, 41.4, 30.6);
        testGeoCoding(target, 0.0, patchSize, 39.6, 28.8);
        testGeoCoding(target, patchSize, patchSize, 41.4, 28.8);
        assertEquals(patchSize, (41.4 - 39.6) / pixelRes, 1e-8);
        assertEquals(patchSize, (30.6 - 28.8) / pixelRes, 1e-8);

        testGeoCoding(target, 0.0, 0.0, 39.6, 30.6);
        testGeoCoding(target, targetWidth, 0.0, 50.4, 30.6);
        testGeoCoding(target, 0.0, targetHeight, 39.6, 9.0);
        testGeoCoding(target, targetWidth, targetHeight, 50.4, 9.0);
        assertEquals(targetWidth, (50.4 - 39.6) / pixelRes, 1e-8);
        assertEquals(targetHeight, (30.6 - 9.0) / pixelRes, 1e-8);
    }

    private void testGeoCoding(Product target, double x, double y, double expectedLon, double expectedLat) {
        GeoPos geoPos = target.getGeoCoding().getGeoPos(new PixelPos(x, y), null);
        assertEquals(expectedLon, geoPos.lon, 1e-5);
        assertEquals(expectedLat, geoPos.lat, 1e-5);
    }

    @Test
    public void testGetLowerBoundGeoPos() throws Exception {
        PatchCS cs = new PatchCS(1, 0.1);
        GeoPos result = cs.getLowerBoundGeoPos(new GeoPos(-1.24, +3.86),
                                               new GeoPos(-2.78, -2.02),
                                               new GeoPos(+3.91, -1.08),
                                               new GeoPos(+1.55, +3.98));
        assertEquals(-2.8, result.lat, 1e-10);
        assertEquals(-2.1, result.lon, 1e-10);
    }

    @Test
    public void testGetUpperBoundGeoPos() throws Exception {
        PatchCS cs = new PatchCS(10, 0.01);
        GeoPos result = cs.getUpperBoundGeoPos(new GeoPos(-1.24, +3.86),
                                               new GeoPos(-2.78, -2.02),
                                               new GeoPos(+3.91, -1.08),
                                               new GeoPos(+1.55, +3.98));
        assertEquals(+4.0, result.lat, 1e-10);
        assertEquals(+4.0, result.lon, 1e-10);
    }

    @Test
    public void testLowerBoundCoordinate() throws Exception {
        PatchCS cs = new PatchCS(1, 0.5);
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
        PatchCS cs = new PatchCS(2, 0.25);
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

    private void testLowerBoundCoordinate(PatchCS cs, double expected, double value) {
        assertEquals(expected, cs.getLowerBoundGeoPos(value), 1e-10);
    }

    private void testUpperBoundCoordinate(PatchCS cs, double expected, double value) {
        assertEquals(expected, cs.getUpperBoundCoordinate(value), 1e-10);
    }
}
