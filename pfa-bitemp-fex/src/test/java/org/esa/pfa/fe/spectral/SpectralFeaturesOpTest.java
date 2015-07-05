package org.esa.pfa.fe.spectral;

import org.esa.snap.framework.datamodel.Product;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Spectral feature operator.
 */
public class SpectralFeaturesOpTest {

    private Product sp1;
    private Product sp2;

    @Before
    public void setUp() throws Exception {
        sp1 = new Product("N", "T", 2, 2);
        sp1.addBand("reflec_1", "0.1");
        sp1.addBand("reflec_2", "0.2");
        sp1.addBand("reflec_3", "0.3");
        sp1.addBand("reflec_4", "0.4");

        sp2 = new Product("N", "T", 2, 2);
        sp2.addBand("reflec_1", "0.1");
        sp2.addBand("reflec_2", "0.2");
        sp2.addBand("reflec_3", "0.3");
        sp2.addBand("reflec_4", "-0.4");
    }

    @Test
    public void testNominalCase() throws Exception {
        SpectralFeaturesOp op = new SpectralFeaturesOp();
        op.setParameterDefaultValues();
        op.setParameter("spectralBandNamingPattern", "reflec_.");
        op.setParameter("maskExpression", "X < 1 && Y < 1");
        op.setSourceProduct(sp1);
        Product tp = op.getTargetProduct();

        testTargetProductLayout(tp);

        float[] magnitude = tp.getBand("magnitude").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_1 = tp.getBand("angle_1").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_2 = tp.getBand("angle_2").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_3 = tp.getBand("angle_3").readPixels(0, 0, 1, 1, (float[]) null);

        assertEquals((float) Math.sqrt(0.4 * 0.4 + 0.3 * 0.3 + 0.2 * 0.2 + 0.1 * 0.1), magnitude[0], 1e-5F);
        assertEquals((float) Math.acos(0.1 / Math.sqrt(0.4 * 0.4 + 0.3 * 0.3 + 0.2 * 0.2 + 0.1 * 0.1)), angle_1[0], 1e-5F);
        assertEquals((float) Math.acos(0.2 / Math.sqrt(0.4 * 0.4 + 0.3 * 0.3 + 0.2 * 0.2)), angle_2[0], 1e-5F);
        assertEquals((float) Math.acos(0.3 / Math.sqrt(0.4 * 0.4 + 0.3 * 0.3)), angle_3[0], 1e-5F);

        float[] magnitude_inv = tp.getBand("magnitude").readPixels(1, 1, 1, 1, (float[]) null);
        float[] angle_1_inv = tp.getBand("angle_1").readPixels(1, 1, 1, 1, (float[]) null);
        float[] angle_2_inv = tp.getBand("angle_2").readPixels(1, 1, 1, 1, (float[]) null);
        float[] angle_3_inv = tp.getBand("angle_3").readPixels(1, 1, 1, 1, (float[]) null);

        assertEquals(Float.NaN, magnitude_inv[0], 1e-5F);
        assertEquals(Float.NaN, angle_1_inv[0], 1e-5F);
        assertEquals(Float.NaN, angle_2_inv[0], 1e-5F);
        assertEquals(Float.NaN, angle_3_inv[0], 1e-5F);
    }

    @Test
    public void testLastCompIsNeg() throws Exception {
        SpectralFeaturesOp op = new SpectralFeaturesOp();
        op.setParameterDefaultValues();
        op.setParameter("spectralBandNamingPattern", "reflec_.");
        op.setSourceProduct(sp2);
        Product tp = op.getTargetProduct();

        testTargetProductLayout(tp);

        float[] magnitude = tp.getBand("magnitude").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_1 = tp.getBand("angle_1").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_2 = tp.getBand("angle_2").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_3 = tp.getBand("angle_3").readPixels(0, 0, 1, 1, (float[]) null);

        assertEquals((float) Math.sqrt(0.4 * 0.4 + 0.3 * 0.3 + 0.2 * 0.2 + 0.1 * 0.1), magnitude[0], 1e-5F);
        assertEquals((float) Math.acos(0.1 / Math.sqrt(0.4 * 0.4 + 0.3 * 0.3 + 0.2 * 0.2 + 0.1 * 0.1)), angle_1[0], 1e-5F);
        assertEquals((float) Math.acos(0.2 / Math.sqrt(0.4 * 0.4 + 0.3 * 0.3 + 0.2 * 0.2)), angle_2[0], 1e-5F);
        assertEquals((float) (2 * Math.PI - Math.acos(0.3 / Math.sqrt(0.4 * 0.4 + 0.3 * 0.3))), angle_3[0], 1e-5F);
    }

    private void testTargetProductLayout(Product tp) {
        assertEquals(4, tp.getNumBands());
        assertNotNull(tp.getBand("magnitude"));
        assertNotNull(tp.getBand("angle_1"));
        assertNotNull(tp.getBand("angle_2"));
        assertNotNull(tp.getBand("angle_3"));
        assertNull(tp.getBand("angle_4"));
    }
}
