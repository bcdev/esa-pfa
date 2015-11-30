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
import org.esa.snap.core.util.converters.BooleanExpressionConverter;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * Spectral feature operator.
 */
@OperatorMetadata(
        alias = "SpectralFeaturesOp",
        version = "0.5",
        authors = "Norman Fomferra, Marco Zühlke"
)
public class SpectralFeaturesOp extends PixelOperator {

    @SourceProduct
    private Product sourceProduct;

    @SourceProduct(optional = true)
    private Product sourceProduct2;

    @TargetProduct
    private Product targetProduct;

    @Parameter(label = "Spectral bands", rasterDataNodeType = Band.class)
    private String[] spectralBands;

    @Parameter(label = "Spectral band naming pattern (regex)", defaultValue = "")
    private String spectralBandNamingPattern;

    @Parameter(label = "Mask expression", converter = BooleanExpressionConverter.class)
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

    private transient String effectiveValidMaskExpr;
    private transient int validMaskIndex;
    private transient Band[] spectralBands1;
    private transient Band[] spectralBands2;

    public SpectralFeaturesOp() {
        validMaskIndex = -1;
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        if (validMaskIndex != -1 && !sourceSamples[validMaskIndex].getBoolean()) {
            setInvalid(targetSamples);
            return;
        }
        // see https://en.wikipedia.org/wiki/N-sphere

        int n = spectralBands1.length - 1;

        double[] diffValues = new double[spectralBands1.length];
        double v;
        for (int i = 0; i < spectralBands1.length; i++) {
            if (spectralBands2 != null) {
                double v1 = sourceSamples[i].getDouble();
                double v2 = sourceSamples[spectralBands1.length + i].getDouble();
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

        double[] valueSqSums = new double[spectralBands1.length];
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
                targetSamples[spectralBands1.length + i].set(diffValues[i]);
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
        for (int i = 1; i < spectralBands1.length; i++) {
            band = p.addBand("angle_" + i, ProductData.TYPE_FLOAT32);
            band.setGeophysicalNoDataValue(Double.NaN);
            band.setNoDataValueUsed(true);
        }
        if (outputDiffs) {
            for (Band spectralBand : spectralBands1) {
                String bandName = "diff_" + spectralBand.getName();
                band = p.addBand(bandName, ProductData.TYPE_FLOAT32);
                band.setGeophysicalNoDataValue(Double.NaN);
                band.setNoDataValueUsed(true);
                if (maxDiff != null) {
                    addDifferenceImageInfo(band);
                }
            }
        }

        initEffectiveValidMaskExpr();
    }

    @Override
    protected void configureSourceSamples(SourceSampleConfigurer sampleConfigurer) throws OperatorException {
        int n = spectralBands1.length;
        for (int i = 0; i < n; i++) {
            sampleConfigurer.defineSample(i, spectralBands1[i].getName());
        }
        if (spectralBands2 != null) {
            for (int i = 0; i < n; i++) {
                sampleConfigurer.defineSample(n + i, spectralBands2[i].getName(), spectralBands2[i].getProduct());
            }
        }
        if (!effectiveValidMaskExpr.isEmpty()) {
            if (sourceProduct2 != null) {
                sourceProduct.setRefNo(1);
                sourceProduct2.setRefNo(2);
                validMaskIndex = spectralBands1.length * 2;
                sampleConfigurer.defineComputedSample(validMaskIndex, ProductData.TYPE_UINT8, effectiveValidMaskExpr, sourceProduct, sourceProduct2);
            } else {
                validMaskIndex = spectralBands2 == null ? spectralBands1.length : spectralBands1.length * 2;
                sampleConfigurer.defineComputedSample(validMaskIndex, ProductData.TYPE_UINT8, effectiveValidMaskExpr, sourceProduct);
            }
        }
    }

    @Override
    protected void configureTargetSamples(TargetSampleConfigurer sampleConfigurer) throws OperatorException {
        sampleConfigurer.defineSample(0, "magnitude");
        for (int i = 1; i < spectralBands1.length; i++) {
            sampleConfigurer.defineSample(i, "angle_" + i);
        }
        if (outputDiffs) {
            for (int i = 0; i < spectralBands1.length; i++) {
                String bandName = "diff_" + spectralBands1[i].getName();
                sampleConfigurer.defineSample(spectralBands1.length + i, bandName);
            }
        }
    }

    @Override
    protected void prepareInputs() throws OperatorException {
        super.prepareInputs();
        if (spectralBands == null || spectralBands.length == 0) {
            spectralBands1 = collectSourceBands();
        } else {
            spectralBands1 = new Band[spectralBands.length];
            for (int i = 0; i < spectralBands.length; i++) {
                Band band = sourceProduct.getBand(spectralBands[i]);
                if (band == null) {
                    throw new OperatorException(String.format("Band '%s' not found in source product.", spectralBands[i]));
                }
                spectralBands1[i] = band;
            }
        }
        if (spectralBands1.length == 0) {
            throw new OperatorException("No source bands defined.");
        }
        if (sourceProduct2 != null) {
            spectralBands2 = new Band[spectralBands1.length];
            for (int i = 0; i < spectralBands1.length; i++) {
                Band sourceBand = spectralBands1[i];
                String bandName = sourceBand.getName();
                spectralBands2[i] = sourceProduct2.getBand(bandName);
                if (spectralBands2[i] == null) {
                    throw new OperatorException(String.format("Band '%s' not found in 2nd source product.", bandName));
                }
            }
        } else if (expectsCollocatedSourceProduct()) {
            spectralBands2 = new Band[spectralBands1.length];
            for (int i = 0; i < spectralBands1.length; i++) {
                Band sourceBand = spectralBands1[i];
                String bandName1 = sourceBand.getName();
                int endIndex = bandName1.length() - source1Suffix.length();
                if (endIndex <= 0) {
                    throw new OperatorException(String.format("No counterpart for band '%s' found in source product.", bandName1));
                }
                String bandName2 = bandName1.substring(0, endIndex) + source2Suffix;
                spectralBands2[i] = sourceProduct.getBand(bandName2);
                if (spectralBands2[i] == null) {
                    throw new OperatorException(String.format("Band '%s' not found in source product.", bandName2));
                }
            }
        }
    }

    private Band[] collectSourceBands() {
        Band[] bands = sourceProduct.getBands();
        ArrayList<Band> sourceBandList = new ArrayList<>();
        if (spectralBandNamingPattern != null && !spectralBandNamingPattern.isEmpty()) {
            Pattern pattern = Pattern.compile(spectralBandNamingPattern);
            if (expectsCollocatedSourceProduct()) {
                for (Band band : bands) {
                    String bandName = band.getName();
                    if (bandName.length() > source1Suffix.length() && bandName.endsWith(source1Suffix)) {
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

    private void addDifferenceImageInfo(Band band) {
        final ColorPaletteDef.Point[] points = new ColorPaletteDef.Point[3];
        points[0] = new ColorPaletteDef.Point(-maxDiff, Color.RED);
        points[1] = new ColorPaletteDef.Point(0, Color.WHITE);
        points[2] = new ColorPaletteDef.Point(maxDiff, Color.BLUE);
        band.setImageInfo(new ImageInfo(new ColorPaletteDef(points)));
    }

    private boolean expectsCollocatedSourceProduct() {
        return source1Suffix != null && !source1Suffix.isEmpty() &&
                source2Suffix != null && !source2Suffix.isEmpty();
    }

    private void initEffectiveValidMaskExpr() {
        HashSet<String> validExpressions = new HashSet<>();
        collectValidMaskExpressions(this.spectralBands1, validExpressions);
        if (spectralBands2 != null) {
            collectValidMaskExpressions(this.spectralBands2, validExpressions);
        }
        if (maskExpression != null) {
            validExpressions.add(maskExpression);
        }
        StringBuilder sb = new StringBuilder();
        for (String validExpression : validExpressions) {
            if (sb.length() > 0) {
                sb.append("&&");
            }
            sb.append(String.format("(%s)", validExpression));
        }
        effectiveValidMaskExpr = sb.toString();
    }

    private void collectValidMaskExpressions(Band[] bands, HashSet<String> validExpressions) {
        for (Band spectralBand : bands) {
            String expression = spectralBand.getValidMaskExpression();
            if (expression != null && !expression.trim().isEmpty()) {
                validExpressions.add(expression);
            }
        }
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(SpectralFeaturesOp.class);
        }
    }
}
