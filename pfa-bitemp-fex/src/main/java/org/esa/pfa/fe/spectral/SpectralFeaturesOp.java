package org.esa.pfa.fe.spectral;

import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.ProductData;
import org.esa.snap.framework.gpf.OperatorException;
import org.esa.snap.framework.gpf.annotations.Parameter;
import org.esa.snap.framework.gpf.annotations.SourceProduct;
import org.esa.snap.framework.gpf.annotations.TargetProduct;
import org.esa.snap.framework.gpf.pointop.PixelOperator;
import org.esa.snap.framework.gpf.pointop.ProductConfigurer;
import org.esa.snap.framework.gpf.pointop.Sample;
import org.esa.snap.framework.gpf.pointop.SampleConfigurer;
import org.esa.snap.framework.gpf.pointop.WritableSample;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Spectral feature operator.
 */
public class SpectralFeaturesOp extends PixelOperator {

    @SourceProduct
    private Product sourceProduct;

    @SourceProduct(optional = true)
    private Product sourceProduct2;

    @TargetProduct
    private Product targetProduct;

    @Parameter(label = "Spectral bands", rasterDataNodeType = Band.class)
    private Band[] spectralBands;

    @Parameter(label = "Spectral band naming pattern (regex)", defaultValue = "")
    private String spectralBandNamingPattern;

    public SpectralFeaturesOp() {
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {

        // see https://en.wikipedia.org/wiki/N-sphere

        int n = spectralBands.length - 1;

        double[] values = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double v1 = sourceSamples[i].getDouble();
            double v2 = sourceProduct2 != null ? sourceSamples[n + 1 + i].getDouble() : 0.0;
            double dv = v1 - v2;
            values[i] = dv;
        }

        double[] valueSqSums = new double[n + 1];
        double valueSqSum = 0;
        for (int i = n; i >= 0; i--) {
            double dv = values[i];
            valueSqSum += dv * dv;
            valueSqSums[i] = Math.sqrt(valueSqSum);
        }

        // Set magnitude
        targetSamples[0].set(valueSqSums[0]);
        double angle;
        for (int i = 1; i <= n; i++) {
            // Set angle_<i>
            valueSqSum = valueSqSums[i - 1];
            if (valueSqSum > 0.0) {
                angle = Math.acos(values[i - 1] / valueSqSum);
            }else {
                angle = 0.0;
            }
            targetSamples[i].set(angle);
        }

        // Special treatment for for angle_<n>
        if (values[n] < 0.0) {
            angle = 2.0 * Math.PI - targetSamples[n].getDouble();
            targetSamples[n].set(angle);
        }
    }

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        Product p = productConfigurer.getTargetProduct();
        productConfigurer.copyMetadata();
        productConfigurer.copyGeoCoding();
        productConfigurer.copyTimeCoding();

        Band band = p.addBand("magnitude", ProductData.TYPE_FLOAT32);
        band.setGeophysicalNoDataValue(Double.NaN);
        band.setNoDataValueUsed(true);
        for (int i = 1; i < spectralBands.length; i++) {
            p.addBand("angle_" + i, ProductData.TYPE_FLOAT32);
            band.setGeophysicalNoDataValue(Double.NaN);
            band.setNoDataValueUsed(true);
        }
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        int n = spectralBands.length;
        for (int i = 0; i < n; i++) {
            sampleConfigurer.defineSample(i, spectralBands[i].getName());
        }
        if (sourceProduct2 != null) {
            for (int i = 0; i < n; i++) {
                sampleConfigurer.defineSample(n + i, spectralBands[i].getName(), sourceProduct2);
            }
        }
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        sampleConfigurer.defineSample(0, "magnitude");
        for (int i = 1; i < spectralBands.length; i++) {
            sampleConfigurer.defineSample(i, "angle_" + i);
        }
    }

    @Override
    protected void prepareInputs() throws OperatorException {

        if (spectralBands == null || spectralBands.length == 0) {
            Band[] bands = sourceProduct.getBands();
            ArrayList<Band> sourceBandList = new ArrayList<>();
            if (spectralBandNamingPattern != null && !spectralBandNamingPattern.isEmpty()) {
                Pattern pattern = Pattern.compile(spectralBandNamingPattern);
                for (Band band : bands) {
                    if (pattern.matcher(band.getName()).matches()) {
                        sourceBandList.add(band);
                    }
                }
            } else {
                for (Band band : bands) {
                    float spectralWavelength = band.getSpectralWavelength();
                    if (spectralWavelength > 0) {
                        sourceBandList.add(band);
                    }
                }
            }
            spectralBands = sourceBandList.toArray(new Band[sourceBandList.size()]);
        }
        if (spectralBands.length == 0) {
            throw new OperatorException("No source bands defined.");
        }

        if (sourceProduct2 != null) {
            for (Band sourceBand : spectralBands) {
                Band sourceBand2 = sourceProduct2.getBand(sourceBand.getName());
                if (sourceBand2 == null) {
                    throw new OperatorException("Band '" + sourceBand.getName() + "' not found in 2nd source product.");
                }
            }
        }

        super.prepareInputs();
    }
}
