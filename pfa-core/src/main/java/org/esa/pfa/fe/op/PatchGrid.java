package org.esa.pfa.fe.op;

import org.geotools.geometry.DirectPosition2D;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;

/**
 * Represents a global patch grid in equirectangular projection.
 *
 * @author Ralf Quast
 */
public class PatchGrid {

    private final AffineTransform t;
    private final int easting = -180;
    private final int northing = 90;
    private final double resolution;
    private final double patchSize;

    public PatchGrid(double resolution, double patchSize) {
        this.resolution = resolution;
        this.patchSize = patchSize;

        t = new AffineTransform();
        t.translate(easting, northing);
        t.scale(resolution, -resolution);
        t.translate(0.0, 0.0);
    }

    /**
     * Returns a transform that transforms from image to map coordinates.
     *
     * @return the transform.
     */
    public AffineTransform getImageToMapTransform() {
        return new AffineTransform(t);
    }

    /**
     * Returns the patch index in x-direction.
     *
     * @param lon A longitude (-180.0, 180) within the patch.
     *
     * @return the patch index in x-direction.
     */
    public int getPatchX(double lon) {
        return (int) (((lon - easting) / resolution) / patchSize);
    }

    /**
     * Returns the patch index in y-direction.
     *
     * @param lat A latitude (-90.0, 90) with the patch.
     *
     * @return the patch index in y-direction.
     */
    public int getPatchY(double lat) {
        return (int) (((northing - lat) / resolution) / patchSize);
    }


    /**
     * Returns the patch coordinates for a tile rectangle.
     *
     * @param r The tile rectangle (only {@code r.x} and {@code r.y} are used).
     * @param t The image-to-map transform used for the tile.
     *
     * @return the patch coordinates.
     *
     * @throws TransformException
     */
    public Point getPatchCoordinates(Rectangle r, MathTransform t) throws TransformException {
        final DirectPosition p = new DirectPosition2D(r.getX(), r.getY());
        t.transform(p, p);

        final double lon = p.getOrdinate(0);
        final double lat = p.getOrdinate(1);

        final int patchX = getPatchX(lon);
        final int patchY = getPatchY(lat);

        return new Point(patchX, patchY);
    }
}
