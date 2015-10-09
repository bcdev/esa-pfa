package org.esa.pfa.fe.spectral;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.gpf.pointop.PixelOperator;
import org.esa.snap.core.gpf.pointop.ProductConfigurer;
import org.esa.snap.core.gpf.pointop.Sample;
import org.esa.snap.core.gpf.pointop.SourceSampleConfigurer;
import org.esa.snap.core.gpf.pointop.TargetSampleConfigurer;
import org.esa.snap.core.gpf.pointop.WritableSample;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Spectral feature operator.
 */
@OperatorMetadata(
        alias = "SpectralFeaturesOp",
        version = "0.5",
        authors = "Norman Fomferra"
)
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

    @Parameter(label = "Mask expression")
    private String maskExpression;

    @Parameter(label = "Logarithmize sources")
    private boolean logSources;

    private int validMaskIndex = -1;

    public SpectralFeaturesOp() {
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        if (validMaskIndex != -1 && !sourceSamples[validMaskIndex].getBoolean()) {
            setInvalid(targetSamples);
            return;
        }
        // see https://en.wikipedia.org/wiki/N-sphere

        int n = spectralBands.length - 1;

        double[] values = new double[n + 1];
        double v;
        for (int i = 0; i <= n; i++) {
            if (sourceProduct2 != null) {
                double v1 = sourceSamples[i].getDouble();
                double v2 = sourceSamples[n + 1 + i].getDouble();
                v = v1 - v2;
                if (logSources) {
                    v = Math.log(v1 / v2);
                } else {
                    v = v1 - v2;
                }
            } else {
                v = sourceSamples[i].getDouble();
                if (logSources) {
                    v = Math.log(v);
                }
            }
            values[i] = v;
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
            } else {
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
            band = p.addBand("angle_" + i, ProductData.TYPE_FLOAT32);
            band.setGeophysicalNoDataValue(Double.NaN);
            band.setNoDataValueUsed(true);
        }
    }

    @Override
    protected void configureSourceSamples(SourceSampleConfigurer sampleConfigurer) throws OperatorException {
        int n = spectralBands.length;
        for (int i = 0; i < n; i++) {
            sampleConfigurer.defineSample(i, spectralBands[i].getName());
        }
        if (sourceProduct2 != null) {
            for (int i = 0; i < n; i++) {
                sampleConfigurer.defineSample(n + i, spectralBands[i].getName(), sourceProduct2);
            }
        }
        if (maskExpression != null && !maskExpression.isEmpty()) {
            if (sourceProduct2 != null) {
                sourceProduct.setRefNo(1);
                sourceProduct2.setRefNo(2);
                validMaskIndex = spectralBands.length * 2;
                sampleConfigurer.defineComputedSample(validMaskIndex, ProductData.TYPE_UINT8, maskExpression, sourceProduct, sourceProduct2);
            } else {
                validMaskIndex = spectralBands.length;
                sampleConfigurer.defineComputedSample(validMaskIndex, ProductData.TYPE_UINT8, maskExpression, sourceProduct);
            }
        }
    }

    @Override
    protected void configureTargetSamples(TargetSampleConfigurer sampleConfigurer) throws OperatorException {
        sampleConfigurer.defineSample(0, "magnitude");
        for (int i = 1; i < spectralBands.length; i++) {
            sampleConfigurer.defineSample(i, "angle_" + i);
        }
    }

    @Override
    protected void prepareInputs() throws OperatorException {
        super.prepareInputs();
        if (spectralBands == null || spectralBands.length == 0) {
            spectralBands = collectSourceBands();
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
    }

    private Band[] collectSourceBands() {
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
        sourceBandList.sort((b1, b2) -> {
            float d = b1.getSpectralWavelength() - b2.getSpectralWavelength();
            return d < 0F ? -1 : d > 0F ? 1 : 0;
        });
        return sourceBandList.toArray(new Band[sourceBandList.size()]);
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(SpectralFeaturesOp.class);
        }
    }
}
