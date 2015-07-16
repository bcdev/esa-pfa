package org.esa.pfa.fe.spectral;

import org.esa.snap.framework.datamodel.GeoPos;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.gpf.operators.standard.reproject.ReprojectionOp;
import org.esa.snap.util.ProductUtils;

import java.awt.Point;

/**
 * The global patch coordinate system.
 *
 * @author Norman
 */
public class PatchCS {
    public static final String PATCH_NAME_FORMAT = "X%04dY%04d";

    private final double patchExtend;
    private final int patchSize;
    private final double pixelResolution;

    /**
     * @param patchSize Patch size in pixels
     * @param pixelResolution Pixel resolution in degrees
     */
    public PatchCS(int patchSize, double pixelResolution) {
        this.patchSize = patchSize;
        this.pixelResolution = pixelResolution;
        this.patchExtend = patchSize * pixelResolution;
    }

    public int getPatchSize() {
        return patchSize;
    }

    public double getPixelResolution() {
        return pixelResolution;
    }

    public double getPatchExtend() {
        return patchExtend;
    }

    public Point getPatchIndex(double lat, double lon) {
        int patchX = (int) ((lon + 180.0) / patchExtend);
        int patchY = (int) ((90.0 - lat) / patchExtend);
        return new Point(patchX, patchY);
    }

    public Product getReprojectedProduct(Product source) {
        GeoPos[] geoBoundary = ProductUtils.createGeoBoundary(source, patchSize);

        int normalization = ProductUtils.normalizeGeoPolygon(geoBoundary);

        GeoPos lowerBoundGeoPos = getLowerBoundGeoPos(geoBoundary);
        GeoPos upperBoundGeoPos = getUpperBoundGeoPos(geoBoundary);

        double rawWidth = (upperBoundGeoPos.lon - lowerBoundGeoPos.lon) / pixelResolution;
        double rawHeight = (upperBoundGeoPos.lat - lowerBoundGeoPos.lat) / pixelResolution;

        int targetWidth = patchSize * (int) Math.floor((rawWidth + patchSize - 1) / patchSize);
        int targetHeight = patchSize * (int) Math.floor((rawHeight + patchSize - 1) / patchSize);

        if (normalization != 0) {
            ProductUtils.denormalizeGeoPos(lowerBoundGeoPos);
        }

        double easting = lowerBoundGeoPos.lon;
        double northing = lowerBoundGeoPos.lat + targetHeight * pixelResolution;

        Point patchIndex = getPatchIndex(northing, easting);
        int patchX = patchIndex.x;
        int patchY = patchIndex.y;

        ReprojectionOp op = new ReprojectionOp();
        op.setParameterDefaultValues();
        op.setSourceProduct(source);
        op.setParameter("crs",
                                    "GEOGCS[\"WGS84(DD)\",\n" +
                                            "DATUM[\"WGS84\",\n" +
                                            "SPHEROID[\"WGS84\", 6378137.0, 298.257223563]],\n" +
                                            "PRIMEM[\"Greenwich\", 0.0],\n" +
                                            "UNIT[\"degree\", 0.017453292519943295],\n" +
                                            "AXIS[\"Geodetic longitude\", EAST],\n" +
                                            "AXIS[\"Geodetic latitude\", NORTH]]");
        op.setParameter("resampling", "Nearest");
        op.setParameter("referencePixelX", 0.0);
        op.setParameter("referencePixelY", 0.0);
        op.setParameter("orientation", 0.0);
        op.setParameter("northing", northing);
        op.setParameter("easting", easting);
        op.setParameter("pixelSizeX", pixelResolution);
        op.setParameter("pixelSizeY", pixelResolution);
        op.setParameter("width", targetWidth);
        op.setParameter("height", targetHeight);
        op.setParameter("noDataValue", Double.NaN);
        op.setParameter("includeTiePointGrids", true);

        Product targetProduct = op.getTargetProduct();
        targetProduct.setName(String.format(PATCH_NAME_FORMAT, patchX, patchY));
        targetProduct.setPreferredTileSize(patchSize, patchSize);

        return targetProduct;
    }

    public double getLowerBoundGeoPos(double v) {
        return Math.floor(v / patchExtend) * patchExtend;
    }

    public double getUpperBoundCoordinate(double v) {
        return Math.floor((v + patchExtend) / patchExtend) * patchExtend;
    }

    public GeoPos getLowerBoundGeoPos(GeoPos... positions) {
        if (positions.length == 0) {
            return new GeoPos(Double.NaN, Double.NaN);
        }
        GeoPos result = new GeoPos(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        for (GeoPos position : positions) {
            result.lat = Math.min(result.lat, getLowerBoundGeoPos(position.lat));
            result.lon = Math.min(result.lon, getLowerBoundGeoPos(position.lon));
        }
        return  result;
    }

    public GeoPos getUpperBoundGeoPos(GeoPos ... positions) {
        if (positions.length == 0) {
            return new GeoPos(Double.NaN, Double.NaN);
        }
        GeoPos result = new GeoPos(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        for (GeoPos position : positions) {
            result.lat = Math.max(result.lat, getUpperBoundCoordinate(position.lat));
            result.lon = Math.max(result.lon, getUpperBoundCoordinate(position.lon));
        }
        return  result;
    }
}
