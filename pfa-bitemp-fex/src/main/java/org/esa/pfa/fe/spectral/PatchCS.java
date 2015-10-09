package org.esa.pfa.fe.spectral;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.common.reproject.ReprojectionOp;
import org.esa.snap.core.util.ProductUtils;

import java.awt.Point;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;

/**
 * The global patch coordinate system.
 *
 * @author Norman
 */
public class PatchCS {
    public static final String PATCH_X_FORMAT = "X%04d";
    public static final String PATCH_Y_FORMAT = "Y%04d";
    public static final String PATCH_NAME_FORMAT = PATCH_X_FORMAT + PATCH_Y_FORMAT;

    private static final GeometryFactory geometryFactory = new GeometryFactory();

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

    public Geometry getPatchGeometry(int tileX, int tileY) {
        double x1 = tileXToDegree(tileX);
        double x2 = tileXToDegree(tileX + 1);
        double y1 = tileYToDegree(tileY);
        double y2 = tileYToDegree(tileY + 1);
        return geometryFactory.toGeometry(new Envelope(x1, x2, y1, y2));
    }

    private double tileXToDegree(int tileX) {
        return ((tileX * patchExtend) - 180.0);
    }

    private double tileYToDegree(int tileY) {
        return 90.0 - (tileY * patchExtend);
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

    static Geometry computeProductGeometry(Product product) {
        final GeneralPath[] paths = ProductUtils.createGeoBoundaryPaths(product);
        final com.vividsolutions.jts.geom.Polygon[] polygons = new com.vividsolutions.jts.geom.Polygon[paths.length];
        for (int i = 0; i < paths.length; i++) {
            polygons[i] = convertAwtPathToJtsPolygon(paths[i]);
        }
        final DouglasPeuckerSimplifier peuckerSimplifier = new DouglasPeuckerSimplifier(
                polygons.length == 1 ? polygons[0] : geometryFactory.createMultiPolygon(polygons));
        return peuckerSimplifier.getResultGeometry();
    }

    private static com.vividsolutions.jts.geom.Polygon convertAwtPathToJtsPolygon(Path2D path) {
        final PathIterator pathIterator = path.getPathIterator(null);
        ArrayList<double[]> coordList = new ArrayList<>();
        int lastOpenIndex = 0;
        while (!pathIterator.isDone()) {
            final double[] coords = new double[6];
            final int segType = pathIterator.currentSegment(coords);
            if (segType == PathIterator.SEG_CLOSE) {
                // we should only detect a single SEG_CLOSE
                coordList.add(coordList.get(lastOpenIndex));
                lastOpenIndex = coordList.size();
            } else {
                coordList.add(coords);
            }
            pathIterator.next();
        }
        final Coordinate[] coordinates = new Coordinate[coordList.size()];
        for (int i1 = 0; i1 < coordinates.length; i1++) {
            final double[] coord = coordList.get(i1);
            coordinates[i1] = new Coordinate(coord[0], coord[1]);
        }

        return geometryFactory.createPolygon(geometryFactory.createLinearRing(coordinates), null);
    }

}
