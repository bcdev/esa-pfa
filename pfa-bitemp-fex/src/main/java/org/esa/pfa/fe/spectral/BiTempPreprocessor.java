package org.esa.pfa.fe.spectral;

import com.bc.ceres.core.Assert;
import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.snap.framework.dataio.ProductIO;
import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.GeoCoding;
import org.esa.snap.framework.datamodel.GeoPos;
import org.esa.snap.framework.datamodel.MetadataElement;
import org.esa.snap.framework.datamodel.PixelPos;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.ProductData;
import org.esa.snap.framework.gpf.OperatorException;
import org.esa.snap.gpf.operators.standard.SubsetOp;
import org.esa.snap.util.SystemUtils;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Norman
 */
public class BiTempPreprocessor {

    public static final DateFormat DATE_FORMAT = ProductData.UTC.createDateFormat("yyyyMMddHHmm");

    private PatchCS patchCS;
    private Path targetDir;

    // Processing time stats
    private long patchCount;
    private double patchTimeSum;
    private long productCount;
    private double productTimeSum;

    public BiTempPreprocessor() {
    }

    void process(Product product) throws IOException {

        Product reprojectedProduct = patchCS.getReprojectedProduct(product);
        reprojectedProduct.addMask("ROI", "!l1_flags.BRIGHT && l1_flags.LAND_OCEAN", "Region of interest", Color.GREEN, 0.7);
        String spectralBandName = null;
        Band[] bands = reprojectedProduct.getBands();
        for (Band band : bands) {
            if (band.getSpectralWavelength() > 0) {
                band.setValidPixelExpression("ROI");
                spectralBandName = band.getName();
            }
        }

        System.out.printf("Reprojected product raster size is %d x %d pixels",
                          reprojectedProduct.getSceneRasterWidth(),
                          reprojectedProduct.getSceneRasterHeight());

        if (spectralBandName == null) {
            throw new IOException("Invalid source product");
        }

        GeoCoding geoCoding = reprojectedProduct.getGeoCoding();
        GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(0, 0), null);

        Point patchIndex = patchCS.getPatchIndex(geoPos.lat, geoPos.lon);

        int patchY = patchIndex.y;
        for (int y = 0; y < reprojectedProduct.getSceneRasterHeight(); y += patchCS.getPatchSize()) {
            int patchX = patchIndex.x;
            for (int x = 0; x < reprojectedProduct.getSceneRasterWidth(); x += patchCS.getPatchSize()) {
                SubsetOp subsetOp = new SubsetOp();
                subsetOp.setParameterDefaultValues();
                subsetOp.setSourceProduct(reprojectedProduct);
                subsetOp.setRegion(new Rectangle(x, y, patchCS.getPatchSize(), patchCS.getPatchSize()));
                subsetOp.setSubSamplingX(1);
                subsetOp.setSubSamplingY(1);
                subsetOp.setCopyMetadata(false);

                Product subsetProduct = subsetOp.getTargetProduct();
                ProductData.UTC startTime = subsetProduct.getStartTime();

                String patchIdX = String.format(PatchCS.PATCH_X_FORMAT, patchX);
                String patchIdY = String.format(PatchCS.PATCH_Y_FORMAT, patchY);
                String patchIdT = "T" + DATE_FORMAT.format(startTime.getAsDate());

                subsetProduct.setStartTime(reprojectedProduct.getStartTime());
                subsetProduct.setEndTime(reprojectedProduct.getEndTime());
                subsetProduct.setName(String.format("%s_%s_%s", patchIdX, patchIdY, patchIdT));
                MetadataElement metadataRoot = subsetProduct.getMetadataRoot();
                MetadataElement globalsAttributes = new MetadataElement("Global_Attributes");
                globalsAttributes.setAttributeString("sourceName", product.getName());
                globalsAttributes.setAttributeString("sourceFile", product.getFileLocation().getPath());
                metadataRoot.addElement(globalsAttributes);

                Band spectralBand = subsetProduct.getBand(spectralBandName);
                Assert.state(spectralBand != null, String.format("spectralBand != null, where is '%s'?", spectralBandName));
                MultiLevelImage validMaskImage = spectralBand.getValidMaskImage();
                double validPixelRatio = 1;
                if (validMaskImage != null) {
                    validPixelRatio = MaskStats.maskedRatio(validMaskImage);
                }

                if (validPixelRatio > 0.2) {
                    writePatchProduct(subsetProduct, patchIdX, patchIdY);
                } else {
                    System.out.printf("Rejected patch %s with valid-pixel ratio of %.1f%%\n", subsetProduct.getNumBands(), 100 * validPixelRatio);
                }

                patchX++;
            }
            patchY++;
        }
    }

    private void writePatchProduct(Product subsetProduct, String patchIdX, String patchIdY) throws IOException {
        Path outputDir = targetDir.resolve(patchIdX).resolve(patchIdY);
        Path outputFile = outputDir.resolve(subsetProduct.getName() + ".dim");

        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        System.out.println("Writing patch " + outputFile);
        long dt;
        try {
            long t1 = System.currentTimeMillis();
            ProductIO.writeProduct(subsetProduct, outputFile.toFile(), "BEAM-DIMAP", false);
            long t2 = System.currentTimeMillis();
            dt = t2 - t1;
            patchCount++;
            patchTimeSum += dt;
            System.out.printf("Patch written after %d ms (%.1f ms in average)\n", dt, patchTimeSum / patchCount);
        } catch (IOException e) {
            System.err.println("ERROR: " + e.getMessage());
        } finally {
            subsetProduct.dispose();
        }
    }

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.ENGLISH);
        System.setProperty("snap.dataio.reader.tileWidth", "*");
        System.setProperty("snap.dataio.reader.tileHeight", "200");
        SystemUtils.init3rdPartyLibs(BiTempPreprocessor.class);

        new BiTempPreprocessor().run(args);
    }

    private void run(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: " + getClass().getSimpleName() + " <target-dir> <source> [<source> ...]");
            System.exit(1);
        }
        patchCS = new PatchCS(200, 0.009);
        FileFilter fileFilter = file -> {
            String name = file.getName();
            return name.startsWith("MER_RR__1P") && name.endsWith(".N1");
        };
        targetDir = Paths.get(args[0]);
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }
        ArrayList<File> fileList = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            scanFiles(new File(arg), fileFilter, fileList);
        }
        fileList.forEach(this::process);
    }

    private void process(File file) {
        try {
            System.out.println("Processing source product " + file);
            long t1 = System.currentTimeMillis();
            Product product = ProductIO.readProduct(file);
            if (product != null) {
                try {
                    process(product);
                } catch (OperatorException e) {
                    System.err.println("ERROR: " + e.getMessage());
                } finally {
                    product.dispose();
                }
            }
            long t2 = System.currentTimeMillis();
            double dt = (t2 - t1) / 1000.0;
            productTimeSum += dt;
            productCount++;
            System.out.printf("Source product processed after %.2f s (%.2f in average)\n", dt, productTimeSum / productCount);
        } catch (IOException e) {
            System.err.println("ERROR: " + e.getMessage());
        }
    }

    private void scanFiles(File file, FileFilter fileFilter, List<File> fileList) {
        if (file.isDirectory()) {
            File[] entries = file.listFiles(fileFilter);
            if (entries != null) {
                for (File entry : entries) {
                    scanFiles(entry, fileFilter, fileList);
                }
            }
        } else {
            fileList.add(file);
        }
    }

}
