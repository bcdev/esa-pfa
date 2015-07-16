package org.esa.pfa.fe.spectral;

import org.esa.snap.framework.dataio.ProductIO;
import org.esa.snap.framework.datamodel.GeoCoding;
import org.esa.snap.framework.datamodel.GeoPos;
import org.esa.snap.framework.datamodel.PixelPos;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.ProductData;
import org.esa.snap.framework.gpf.OperatorException;
import org.esa.snap.gpf.operators.standard.SubsetOp;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.DateFormat;

/**
 * @author Norman
 */
public class BiTempPreprocessor {

    public static final DateFormat DATE_FORMAT = ProductData.UTC.createDateFormat("yyyyMMddHHmm");

    private PatchCS patchCS;
    private File targetDir;

    void process(Product product) {
        Product reprojectedProduct = patchCS.getReprojectedProduct(product);


        GeoCoding geoCoding = reprojectedProduct.getGeoCoding();
        GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(0, 0), null);

        Point patchIndex = patchCS.getPatchIndex(geoPos.lat, geoPos.lon);
        int patchX0 = patchIndex.x;
        int patchY0 = patchIndex.y;

        double dtSum = 0;
        long patchCount = 0;

        int patchY = patchY0;
        for (int y = 0; y < reprojectedProduct.getSceneRasterHeight(); y += patchCS.getPatchSize()) {
            int patchX = patchX0;
            for (int x = 0; x < reprojectedProduct.getSceneRasterWidth(); x += patchCS.getPatchSize()) {
                SubsetOp subsetOp = new SubsetOp();
                subsetOp.setParameterDefaultValues();
                subsetOp.setSourceProduct(reprojectedProduct);
                subsetOp.setRegion(new Rectangle(x, y, patchCS.getPatchSize(), patchCS.getPatchSize()));
                subsetOp.setSubSamplingX(1);
                subsetOp.setSubSamplingY(1);
                subsetOp.setCopyMetadata(false);

                Product subsetProduct = subsetOp.getTargetProduct();

                String locationId = String.format(PatchCS.PATCH_NAME_FORMAT, patchX, patchY);

                ProductData.UTC startTime = subsetProduct.getStartTime();
                String timeId = DATE_FORMAT.format(startTime.getAsDate());
                String productName = locationId + "T" + timeId;

                subsetProduct.setStartTime(reprojectedProduct.getStartTime());
                subsetProduct.setEndTime(reprojectedProduct.getEndTime());
                subsetProduct.setName(productName);

                File outputFile = new File(new File(targetDir, locationId), productName + ".dim");

                long t1 = System.currentTimeMillis();
                System.out.println("Writing patch " + outputFile);
                long dt = 0;
                try {
                    ProductIO.writeProduct(subsetProduct, outputFile, "BEAM-DIMAP", false);
                    long t2 = System.currentTimeMillis();
                    dt = t2 - t1;
                    patchCount++;
                    dtSum += dt;
                } catch (IOException e) {
                    System.err.println("ERROR: " + e.getMessage());
                } finally {
                    subsetProduct.dispose();
                }

                System.out.printf("Patch written after %d ms (%s ms in average)%n", dt, dtSum / patchCount);

                patchX++;
            }
            patchY++;
        }
    }

    public static void main(String[] args) {
        new BiTempPreprocessor().run(args);
    }

    private void run(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: " + getClass().getSimpleName() + " <target-dir> <source> [<source> ...]");
            System.exit(1);
        }
        patchCS = new PatchCS(200, 0.009);
        FileFilter fileFilter = file -> {
            String name = file.getName();
            return name.startsWith("MER_RR__1P") && name.endsWith(".N1");
        };
        targetDir = new File(args[0]);
        targetDir.mkdirs();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            run(new File(arg), fileFilter);
        }
    }

    private void run(File file, FileFilter fileFilter) {
        if (file.isDirectory()) {
            File[] files = file.listFiles(fileFilter);
            if (files != null) {
                for (File file1 : files) {
                    run((File) file1, (FileFilter) fileFilter);
                }
            }
        } else {
            try {
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
            } catch (IOException e) {
                System.err.println("ERROR: " + e.getMessage());
            }
        }
    }

}
