package org.esa.pfa.fe.spectral;

import com.bc.ceres.core.Assert;
import com.bc.ceres.glevel.MultiLevelImage;
import com.sun.media.jai.util.SunTileCache;
import com.vividsolutions.jts.geom.Geometry;
import org.esa.pfa.fe.PFAApplicationDescriptor;
import org.esa.pfa.fe.PFAApplicationRegistry;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.common.SubsetOp;
import org.esa.snap.core.util.SystemUtils;

import javax.media.jai.JAI;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
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
//    public static final int PATCH_SIZE = 200;
    public static final double PIXEL_RESOLUTION = 0.009;

    private final Path targetDir;
    private final String applicationName;
    private final PFAApplicationDescriptor applicationDescriptor;

    private PatchCS patchCS;

    // Processing time stats
    private long writtenPatchCount;
    private double patchTimeSum;
    private long processedProductCount;
    private double productTimeSum;
    private long recordId;

    private PrintWriter printWriter;

    public BiTempPreprocessor(Path targetDir, String applicationName) throws IOException {
        this.targetDir = targetDir;
        this.applicationName = applicationName;
        this.applicationDescriptor = PFAApplicationRegistry.getInstance().getDescriptorByName(applicationName);

        final int patchSize = applicationDescriptor.getPatchDimension().width;
        patchCS = new PatchCS(patchSize, PIXEL_RESOLUTION);
        System.setProperty("snap.dataio.reader.tileWidth", (patchSize * 2) + "");
        System.setProperty("snap.dataio.reader.tileHeight", (patchSize * 2) + "");

        printWriter = new PrintWriter(new FileWriter("BiTempPreprocessor.csv"));
        printWriter.printf("id\t#products\t#patches\ttime/patch(ms)\tcached(MiB)\tfree(MiB)\ttotal(MiB)\tmax(MiB)%n");
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                printWriter.close();
            }
        });
    }

    void process(Product product) throws IOException {

        Product reprojectedProduct = patchCS.getReprojectedProduct(product);
        reprojectedProduct.addMask("ROI", "!l1_flags.BRIGHT && l1_flags.LAND_OCEAN", "Region of interest", Color.GREEN, 0.7);
        String lastSpectralBandName = null;
        Band[] bands = reprojectedProduct.getBands();
        for (Band band : bands) {
            if (band.getSpectralWavelength() > 0) {
                band.setValidPixelExpression("ROI");
                lastSpectralBandName = band.getName();
            }
        }

        System.out.printf("Reprojected product raster size is %d x %d pixels%n",
                          reprojectedProduct.getSceneRasterWidth(),
                          reprojectedProduct.getSceneRasterHeight());

        if (lastSpectralBandName == null) {
            throw new IOException("Invalid source product");
        }
        Geometry sourceGeometry = PatchCS.computeProductGeometry(product);

        GeoCoding geoCoding = reprojectedProduct.getSceneGeoCoding();
        GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(0, 0), null);

        Point patchIndex = patchCS.getPatchIndex(geoPos.lat, geoPos.lon);

        int patchY = patchIndex.y;
        for (int y = 0; y < reprojectedProduct.getSceneRasterHeight(); y += patchCS.getPatchSize()) {
            int patchX = patchIndex.x;
            for (int x = 0; x < reprojectedProduct.getSceneRasterWidth(); x += patchCS.getPatchSize()) {
                String patchIdX = String.format(PatchCS.PATCH_X_FORMAT, patchX);
                String patchIdY = String.format(PatchCS.PATCH_Y_FORMAT, patchY);

                Geometry patchGeometry = patchCS.getPatchGeometry(patchX, patchY);

                if (sourceGeometry.intersects(patchGeometry)) {
                    double patchArea = patchGeometry.getArea();
                    Geometry intersectionGeometry = patchGeometry.intersection(sourceGeometry);
                    double intersectionArea = intersectionGeometry.getArea();
                    if (intersectionArea > 0.2 * patchArea) {
                        SubsetOp subsetOp = new SubsetOp();
                        subsetOp.setParameterDefaultValues();
                        subsetOp.setSourceProduct(reprojectedProduct);
                        subsetOp.setRegion(new Rectangle(x, y, patchCS.getPatchSize(), patchCS.getPatchSize()));
                        subsetOp.setSubSamplingX(1);
                        subsetOp.setSubSamplingY(1);
                        subsetOp.setCopyMetadata(false);

                        Product subsetProduct = subsetOp.getTargetProduct();
                        try {
                            ProductData.UTC startTime = reprojectedProduct.getStartTime();
                            String patchIdT = "T" + DATE_FORMAT.format(startTime.getAsDate());

                            subsetProduct.setStartTime(reprojectedProduct.getStartTime());
                            subsetProduct.setEndTime(reprojectedProduct.getEndTime());
                            subsetProduct.setName(String.format("%s_%s_%s", patchIdX, patchIdY, patchIdT));
                            MetadataElement metadataRoot = subsetProduct.getMetadataRoot();
                            MetadataElement globalsAttributes = new MetadataElement("Global_Attributes");
                            globalsAttributes.setAttributeString("sourceName", product.getName());
                            globalsAttributes.setAttributeString("sourceFile", product.getFileLocation().getPath());
                            metadataRoot.addElement(globalsAttributes);

                            Band spectralBand = subsetProduct.getBand(lastSpectralBandName);
                            Assert.state(spectralBand != null, String.format("spectralBand != null, where is '%s'?", lastSpectralBandName));
                            MultiLevelImage validMaskImage = spectralBand.getValidMaskImage();
                            double validPixelRatio = 1;
                            if (validMaskImage != null) {
                                validPixelRatio = MaskStats.maskedRatio(validMaskImage);
                            }

                            if (validPixelRatio > 0.2) {
                                System.out.printf("Writing patch %s with valid-pixel ratio of %.1f%%\n", subsetProduct.getName(), 100 * validPixelRatio);
                                try {
                                    writePatchProduct(subsetProduct, patchIdX, patchIdY);
                                } catch (IOException e) {
                                    System.err.println("ERROR: " + e.getMessage());
                                }
                            } else {
                                System.out.printf("Rejected patch %s with valid-pixel ratio of %.1f%%\n", subsetProduct.getName(), 100 * validPixelRatio);
                            }
                        } finally {
                            subsetProduct.dispose();
                        }
                    } else {
                        System.out.printf("Rejected patch %s_%s. Only small intersection with source\n", patchIdX, patchIdY);
                    }
                } else {
                    System.out.printf("Rejected patch %s_%s. No intersection with source\n", patchIdX, patchIdY);
                }

                printWriter.printf("%d\t%d\t%d\t%.1f\t%d\t%d\t%d\t%d%n",
                                   recordId,
                                   processedProductCount,
                                   writtenPatchCount,
                                   writtenPatchCount > 0 ? patchTimeSum / writtenPatchCount : 0.0,
                                   mibs(((SunTileCache) JAI.getDefaultInstance().getTileCache()).getCacheMemoryUsed()),
                                   mibs(Runtime.getRuntime().freeMemory()),
                                   mibs(Runtime.getRuntime().totalMemory()),
                                   mibs(Runtime.getRuntime().maxMemory()));
                recordId++;

                patchX++;
            }
            patchY++;
        }
    }

    private long mibs(long mem) {
        return Math.round(mem / 1024. / 1024.);
    }

    private void writePatchProduct(Product patchProduct, String patchIdX, String patchIdY) throws IOException {
        Path outputDir = targetDir.resolve(patchIdX).resolve(patchIdY);
        Path outputFile = outputDir.resolve(patchProduct.getName() + ".dim");

        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        long t1 = System.currentTimeMillis();
        ProductIO.writeProduct(patchProduct, outputFile.toFile(), "BEAM-DIMAP", false);
        long t2 = System.currentTimeMillis();
        long dt = t2 - t1;
        writtenPatchCount++;
        patchTimeSum += dt;
        System.out.printf("Patch written after %d ms (%.1f ms in average)\n", dt, patchTimeSum / writtenPatchCount);
    }

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.ENGLISH);
        SystemUtils.init3rdPartyLibs(BiTempPreprocessor.class);

        if (args.length < 3) {
            System.out.println("Usage: " + BiTempPreprocessor.class.getSimpleName() + " <target-dir> <application> <source> [<source> ...]");
            System.exit(1);
        }
        Path targetDir = Paths.get(args[0]);
        String application = args[1];
        new BiTempPreprocessor(targetDir, application).run(args);
    }

    private void run(String[] args) throws IOException {
        FileFilter fileFilter = file -> {
            String name = file.getName();
            return name.startsWith("MER_RR__1P") && name.endsWith(".N1");
        };
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }
        ArrayList<File> fileList = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
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
            processedProductCount++;
            System.out.printf("Source product processed after %.2f s (%.2f in average)\n", dt, productTimeSum / processedProductCount);
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
