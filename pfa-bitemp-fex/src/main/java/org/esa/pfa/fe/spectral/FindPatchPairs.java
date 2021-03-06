/*
 * Copyright (C) 2015 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.pfa.fe.spectral;

import com.bc.ceres.core.Assert;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.dataio.dimap.DimapProductConstants;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Find pairs of patches and extracts features from them
 *
 * @author marcoz
 */
public class FindPatchPairs {

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("X\\d+_Y\\d+_T(\\d+).dim");

    private final double minValidPixelRatio;
    private final int minDistanceSeconds;
    private final int maxDistanceSeconds;

    public FindPatchPairs(double minValidPixelRatio, int minDistanceSeconds, int maxDistanceSeconds) {
        this.minValidPixelRatio = minValidPixelRatio;
        this.minDistanceSeconds = minDistanceSeconds;
        this.maxDistanceSeconds = maxDistanceSeconds;
    }

    private void run(File[] patchFiles, Path targetDir) throws ParseException {
        int i1 = 0;
        int i2 = 1;
        while (i2 < patchFiles.length) {
            int distanceSeconds = timeDifferenceInSeconds(patchFiles[i1].getName(), patchFiles[i2].getName());
            if (distanceSeconds < minDistanceSeconds) {
                i2++;
            } else if (distanceSeconds > maxDistanceSeconds) {
                i1++;
                i2 = i1 + 1;
            } else {
                if (findOverlappingPixels(patchFiles[i1], patchFiles[i2], targetDir)) {
                    i1 = i2 + 1;
                    i2 = i1 + 1;
                } else {
                    i2++;
                }
            }
        }
    }

    static int timeDifferenceInSeconds(String fileName1, String fileName2) throws ParseException {
        long t1 = extractTime(fileName1);
        long t2 = extractTime(fileName2);

        return (int) ((t2 - t1) / 1000);
    }

    static long extractTime(String fileName) throws ParseException {
        Matcher matcher = FILE_NAME_PATTERN.matcher(fileName);
        // Must match at least once i.o.t. get the group
        matcher.matches();
        Date date = BiTempPreprocessor.DATE_FORMAT.parse(matcher.group(1));
        return date.getTime();
    }

    private boolean findOverlappingPixels(File file1, File file2, Path targetDir) {
        System.out.println("FindPatchPairs.findOverlappingPixels");
        System.out.println("file1 = [" + file1 + "], file2 = [" + file2 + "], targetDir = [" + targetDir + "]");

        try {
            Product p1 = ProductIO.readProduct(file1);
            Product p2 = ProductIO.readProduct(file2);
            Mask roi1 = p1.getMaskGroup().get("ROI");
            Mask roi2 = p2.getMaskGroup().get("ROI");

            Assert.state(roi1.getRasterWidth() == roi2.getRasterWidth());
            Assert.state(roi1.getRasterHeight() == roi2.getRasterHeight());

            int numPixels = roi1.getRasterWidth() * roi2.getRasterHeight();

            double overlapRatio = MaskStats.countPixels(roi1, roi2) / (double) numPixels;
            System.out.printf("overlapRatio = %.2f%%%n", 100 * overlapRatio);
            if (overlapRatio >= minValidPixelRatio) {
                SpectralFeaturesOp sfOp = new SpectralFeaturesOp();
                sfOp.setParameterDefaultValues();
                sfOp.setParameter("spectralBandNamingPattern", "reflec_."); // TODO  process before reprojection or after ?
                sfOp.setParameter("maskExpression", "$1.ROI AND $2.ROI");
                sfOp.setSourceProduct(p1);
                sfOp.setSourceProduct("sourceProduct2", p2);
                Product tp = sfOp.getTargetProduct();


                String targetName = p1.getName() + "-" + p2.getName();
                File targetFile = targetDir.resolve(targetName).toFile();
                ProductIO.writeProduct(tp, targetFile, DimapProductConstants.DIMAP_FORMAT_NAME, false);

                return true;
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: " + FindPatchPairs.class.getSimpleName() + " <patch-dir> <target-dir>");
            System.exit(1);
        }
        String patchDir = args[0];

        FileFilter dimPatchFilter = file -> FILE_NAME_PATTERN.matcher(file.getName()).matches();
        Path targetDir = Paths.get(args[1]);
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }
        File[] patchFiles = new File(patchDir).listFiles(dimPatchFilter);
        Assert.state(patchFiles.length > 2, "Less than 2 patches; can not find pairs.");
        Arrays.sort(patchFiles, (f1, f2) -> f1.getName().compareTo(f2.getName()));

        double minValidPixelRatio = 0.2; // 20%
        int minDistance = (int) Duration.ofDays(1).getSeconds();
        int maxDistance = (int) Duration.ofDays(7).getSeconds();

        FindPatchPairs findPatchPairs = new FindPatchPairs(minValidPixelRatio, minDistance, maxDistance);
        findPatchPairs.run(patchFiles, targetDir);
    }
}
