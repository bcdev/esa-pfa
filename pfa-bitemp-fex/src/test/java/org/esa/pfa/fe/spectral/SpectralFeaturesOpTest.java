package org.esa.pfa.fe.spectral;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.OperatorException;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * Spectral feature operator.
 */
public class SpectralFeaturesOpTest {

    private Product sp1;
    private Product sp2;
    private Product spNegativ;
    private Product collocated;

    @Before
    public void setUp() throws Exception {
        sp1 = new Product("N", "T", 2, 2);
        sp1.addBand("reflec_1", "0.1");
        sp1.addBand("reflec_2", "0.2");
        sp1.addBand("reflec_3", "0.3");
        sp1.addBand("reflec_4", "0.4");

        sp2 = new Product("N", "T", 2, 2);
        sp2.addBand("reflec_1", "0.01");
        sp2.addBand("reflec_2", "0.02");
        sp2.addBand("reflec_3", "0.03");
        sp2.addBand("reflec_4", "0.04");

        spNegativ = new Product("N", "T", 2, 2);
        spNegativ.addBand("reflec_1", "0.1");
        spNegativ.addBand("reflec_2", "0.2");
        spNegativ.addBand("reflec_3", "0.3");
        spNegativ.addBand("reflec_4", "-0.4");

        collocated = new Product("N", "T", 2, 2);
        collocated.addBand("reflec_1_M", "0.1");
        collocated.addBand("reflec_2_M", "0.2");
        collocated.addBand("reflec_3_M", "0.3");
        collocated.addBand("reflec_4_M", "0.4");
        collocated.addBand("reflec_1_S", "0.01");
        collocated.addBand("reflec_2_S", "0.02");
        collocated.addBand("reflec_3_S", "0.03");
        collocated.addBand("reflec_4_S", "0.04");

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

        double sum43 = 0.4 * 0.4 + 0.3 * 0.3;
        double sum432 = sum43 + 0.2 * 0.2;
        double sum4321 = sum432 + 0.1 * 0.1;
        assertEquals((float) Math.sqrt(sum4321), magnitude[0], 1e-5F);
        assertEquals((float) Math.acos(0.1 / Math.sqrt(sum4321)), angle_1[0], 1e-5F);
        assertEquals((float) Math.acos(0.2 / Math.sqrt(sum432)), angle_2[0], 1e-5F);
        assertEquals((float) Math.acos(0.3 / Math.sqrt(sum43)), angle_3[0], 1e-5F);

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
    public void testNominalCase_2products() throws Exception {
        SpectralFeaturesOp op = new SpectralFeaturesOp();
        op.setParameterDefaultValues();
        op.setParameter("spectralBandNamingPattern", "reflec_.");
        op.setParameter("maskExpression", "X < 1 && Y < 1");
        op.setSourceProduct("sourceProduct", sp1);
        op.setSourceProduct("sourceProduct2", sp2);
        Product tp = op.getTargetProduct();

        testTargetProductLayout(tp);

        float[] magnitude = tp.getBand("magnitude").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_1 = tp.getBand("angle_1").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_2 = tp.getBand("angle_2").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_3 = tp.getBand("angle_3").readPixels(0, 0, 1, 1, (float[]) null);

        double sum43 = (0.4 - 0.04) * (0.4 - 0.04) + (0.3 - 0.03) * (0.3 - 0.03);
        double sum432 = sum43 + (0.2 - 0.02) * (0.2 - 0.02);
        double sum4321 = sum432 + (0.1 - 0.01) * (0.1 - 0.01);
        assertEquals((float) Math.sqrt(sum4321), magnitude[0], 1e-5F);
        assertEquals((float) Math.acos((0.1 - 0.01) / Math.sqrt(sum4321)), angle_1[0], 1e-5F);
        assertEquals((float) Math.acos((0.2 - 0.02) / Math.sqrt(sum432)), angle_2[0], 1e-5F);
        assertEquals((float) Math.acos((0.3 - 0.03) / Math.sqrt(sum43)), angle_3[0], 1e-5F);
    }

    @Test
    public void testNominalCase_CollocatedNameLookup() throws Exception {
        SpectralFeaturesOp op = new SpectralFeaturesOp();
        op.setParameterDefaultValues();
        op.setParameter("spectralBandNamingPattern", "reflec_.");
        op.setParameter("maskExpression", "X < 1 && Y < 1");
        op.setParameter("source1Suffix", "_M");
        op.setParameter("source2Suffix", "_X");
        op.setSourceProduct("sourceProduct", collocated);
        try {
            op.getTargetProduct();
            fail();
        } catch (OperatorException oe) {
            assertEquals("Band 'reflec_1_X' not found in source product.", oe.getMessage());
        }
    }

    @Test
    public void testNominalCase_Collocated() throws Exception {
        SpectralFeaturesOp op = new SpectralFeaturesOp();
        op.setParameterDefaultValues();
        op.setParameter("spectralBandNamingPattern", "reflec_.");
        op.setParameter("maskExpression", "X < 1 && Y < 1");
        op.setParameter("source1Suffix", "_M");
        op.setParameter("source2Suffix", "_S");
        op.setSourceProduct("sourceProduct", collocated);
        Product tp = op.getTargetProduct();

        testTargetProductLayout(tp);

        float[] magnitude = tp.getBand("magnitude").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_1 = tp.getBand("angle_1").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_2 = tp.getBand("angle_2").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_3 = tp.getBand("angle_3").readPixels(0, 0, 1, 1, (float[]) null);

        double sum43 = (0.4 - 0.04) * (0.4 - 0.04) + (0.3 - 0.03) * (0.3 - 0.03);
        double sum432 = sum43 + (0.2 - 0.02) * (0.2 - 0.02);
        double sum4321 = sum432 + (0.1 - 0.01) * (0.1 - 0.01);
        assertEquals((float) Math.sqrt(sum4321), magnitude[0], 1e-5F);
        assertEquals((float) Math.acos((0.1 - 0.01) / Math.sqrt(sum4321)), angle_1[0], 1e-5F);
        assertEquals((float) Math.acos((0.2 - 0.02) / Math.sqrt(sum432)), angle_2[0], 1e-5F);
        assertEquals((float) Math.acos((0.3 - 0.03) / Math.sqrt(sum43)), angle_3[0], 1e-5F);
    }

    @Test
    public void testLastCompIsNeg() throws Exception {
        SpectralFeaturesOp op = new SpectralFeaturesOp();
        op.setParameterDefaultValues();
        op.setParameter("spectralBandNamingPattern", "reflec_.");
        op.setSourceProduct(spNegativ);
        Product tp = op.getTargetProduct();

        testTargetProductLayout(tp);

        float[] magnitude = tp.getBand("magnitude").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_1 = tp.getBand("angle_1").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_2 = tp.getBand("angle_2").readPixels(0, 0, 1, 1, (float[]) null);
        float[] angle_3 = tp.getBand("angle_3").readPixels(0, 0, 1, 1, (float[]) null);

        double sum43 = 0.4 * 0.4 + 0.3 * 0.3;
        double sum432 = 0.4 * 0.4 + 0.3 * 0.3 + 0.2 * 0.2;
        double sum4321 = 0.4 * 0.4 + 0.3 * 0.3 + 0.2 * 0.2 + 0.1 * 0.1;
        assertEquals((float) Math.sqrt(sum4321), magnitude[0], 1e-5F);
        assertEquals((float) Math.acos(0.1 / Math.sqrt(sum4321)), angle_1[0], 1e-5F);
        assertEquals((float) Math.acos(0.2 / Math.sqrt(sum432)), angle_2[0], 1e-5F);
        assertEquals((float) (2 * Math.PI - Math.acos(0.3 / Math.sqrt(sum43))), angle_3[0], 1e-5F);
    }

    @Test
    public void testDifferenceOutput() throws Exception {
        SpectralFeaturesOp op = new SpectralFeaturesOp();
        op.setParameterDefaultValues();
        op.setParameter("spectralBandNamingPattern", "reflec_.");
        op.setParameter("maskExpression", "X < 1 && Y < 1");
        op.setParameter("outputDiffs", "true");
        op.setSourceProduct("sourceProduct", sp1);
        op.setSourceProduct("sourceProduct2", sp2);

        Product tp = op.getTargetProduct();

        assertEquals(8, tp.getNumBands());
        assertThat(
                Arrays.asList(tp.getBandNames()),
                hasItems("magnitude",
                         "angle_1", "angle_2", "angle_3",
                         "diff_reflec_1", "diff_reflec_2", "diff_reflec_3", "diff_reflec_4"));

        float[] dr1 = tp.getBand("diff_reflec_1").readPixels(0, 0, 1, 1, (float[]) null);
        float[] dr2 = tp.getBand("diff_reflec_2").readPixels(0, 0, 1, 1, (float[]) null);
        float[] dr3 = tp.getBand("diff_reflec_3").readPixels(0, 0, 1, 1, (float[]) null);
        float[] dr4 = tp.getBand("diff_reflec_4").readPixels(0, 0, 1, 1, (float[]) null);

        assertEquals((float) 0.1 - 0.01, dr1[0], 1e-5F);
        assertEquals((float) 0.2 - 0.02, dr2[0], 1e-5F);
        assertEquals((float) 0.3 - 0.03, dr3[0], 1e-5F);
        assertEquals((float) 0.4 - 0.04, dr4[0], 1e-5F);
    }

    private void testTargetProductLayout(Product tp) {
        assertEquals(4, tp.getNumBands());
        assertNotNull(tp.getBand("magnitude"));
        assertNotNull(tp.getBand("angle_1"));
        assertNotNull(tp.getBand("angle_2"));
        assertNotNull(tp.getBand("angle_3"));
        assertNull(tp.getBand("angle_4"));

        assertEquals(2, tp.getBand("magnitude").getRasterWidth());
        assertEquals(2, tp.getBand("magnitude").getRasterHeight());
    }
}
