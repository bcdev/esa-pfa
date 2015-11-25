package org.esa.pfa.fe.spectral;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.ColorPaletteDef;
import org.esa.snap.core.datamodel.ImageInfo;
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

import java.awt.*;
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

    @Parameter(label = "Logarithmize sources", defaultValue = "false")
    private boolean logSources;

    @Parameter(label = "Include differences in output", defaultValue = "false")
    private boolean outputDiffs;

    @Parameter(label = "Expected max value of differences")
    private Double maxDiff;

    @Parameter(label = "For collocated products: band name suffix for 1st product")
    private String source1Suffix;

    @Parameter(label = "For collocated products: band name suffix for 2nd product")
    private String source2Suffix;

    private int validMaskIndex = -1;
    private Band[] spectralBands2;

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

        double[] diffValues = new double[spectralBands.length];
        double v;
        for (int i = 0; i < spectralBands.length; i++) {
            if (spectralBands2 != null) {
                double v1 = sourceSamples[i].getDouble();
                double v2 = sourceSamples[spectralBands.length + i].getDouble();
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
            diffValues[i] = v;
        }

        double[] valueSqSums = new double[spectralBands.length];
        double valueSqSum = 0;
        for (int i = n; i >= 0; i--) {
            double dv = diffValues[i];
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
                angle = Math.acos(diffValues[i - 1] / valueSqSum);
            } else {
                angle = 0.0;
            }
            targetSamples[i].set(angle);
        }

        // Special treatment for for angle_<n>
        if (diffValues[n] < 0.0) {
            angle = 2.0 * Math.PI - targetSamples[n].getDouble();
            targetSamples[n].set(angle);
        }
        if (outputDiffs) {
            for (int i = 0; i < diffValues.length; i++) {
                targetSamples[spectralBands.length + i].set(diffValues[i]);
            }
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
        if (outputDiffs) {
            for (Band spectralBand : spectralBands) {
                String bandName = "diff_" + spectralBand.getName();
                band = p.addBand(bandName, ProductData.TYPE_FLOAT32);
                band.setGeophysicalNoDataValue(Double.NaN);
                band.setNoDataValueUsed(true);
                if (maxDiff != null) {
                    addDifferenceImageInfo(band);
                }
            }
        }
    }

    private void addDifferenceImageInfo(Band band) {
        final ColorPaletteDef.Point[] points = new ColorPaletteDef.Point[3];
        points[0] = new ColorPaletteDef.Point(-maxDiff, Color.RED);
        points[1] = new ColorPaletteDef.Point(0, Color.WHITE);
        points[2] = new ColorPaletteDef.Point(maxDiff, Color.BLUE);
        band.setImageInfo(new ImageInfo(new ColorPaletteDef(points)));
    }

    @Override
    protected void configureSourceSamples(SourceSampleConfigurer sampleConfigurer) throws OperatorException {
        int n = spectralBands.length;
        for (int i = 0; i < n; i++) {
            sampleConfigurer.defineSample(i, spectralBands[i].getName());
        }
        if (spectralBands2 != null) {
            for (int i = 0; i < n; i++) {
                sampleConfigurer.defineSample(n + i, spectralBands2[i].getName(), spectralBands2[i].getProduct());
            }
        }
        if (maskExpression != null && !maskExpression.isEmpty()) {
            if (sourceProduct2 != null) {
                sourceProduct.setRefNo(1);
                sourceProduct2.setRefNo(2);
                validMaskIndex = spectralBands.length * 2;
                sampleConfigurer.defineComputedSample(validMaskIndex, ProductData.TYPE_UINT8, maskExpression, sourceProduct, sourceProduct2);
            } else {
                validMaskIndex = spectralBands2 == null ? spectralBands.length : spectralBands.length * 2;
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
        if (outputDiffs) {
            for (int i = 0; i < spectralBands.length; i++) {
                String bandName = "diff_" + spectralBands[i].getName();
                sampleConfigurer.defineSample(spectralBands.length + i, bandName);
            }
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
            spectralBands2 = new Band[spectralBands.length];
            for (int i = 0; i < spectralBands.length; i++) {
                Band sourceBand = spectralBands[i];
                String bandName = sourceBand.getName();
                spectralBands2[i] = sourceProduct2.getBand(bandName);
                if (spectralBands2[i] == null) {
                    throw new OperatorException("Band '" + bandName + "' not found in 2nd source product.");
                }
            }
        } else if (source1Suffix != null && !source1Suffix.isEmpty() &&
                            source2Suffix != null && !source2Suffix.isEmpty()) {
            spectralBands2 = new Band[spectralBands.length];
            for (int i = 0; i < spectralBands.length; i++) {
                Band sourceBand = spectralBands[i];
                String bandName = sourceBand.getName();
                bandName = bandName.substring(0, bandName.length() - source1Suffix.length());
                bandName = bandName + source2Suffix;
                spectralBands2[i] = sourceProduct.getBand(bandName);
                if (spectralBands2[i] == null) {
                    throw new OperatorException("Band '" + bandName + "' not found in source product.");
                }
            }
        }
    }

    private Band[] collectSourceBands() {
        Band[] bands = sourceProduct.getBands();
        ArrayList<Band> sourceBandList = new ArrayList<>();
        if (spectralBandNamingPattern != null && !spectralBandNamingPattern.isEmpty()) {
            Pattern pattern = Pattern.compile(spectralBandNamingPattern);
            if (source1Suffix != null && !source1Suffix.isEmpty() &&
                    source2Suffix != null && !source2Suffix.isEmpty()) {
                for (Band band : bands) {
                    String bandName = band.getName();
                    if (bandName.endsWith(source1Suffix)) {
                        bandName = bandName.substring(0, bandName.length() - source1Suffix.length());
                        if (pattern.matcher(bandName).matches()) {
                            sourceBandList.add(band);
                        }
                    }
                }
            } else {
                for (Band band : bands) {
                    if (pattern.matcher(band.getName()).matches()) {
                        sourceBandList.add(band);
                    }
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
