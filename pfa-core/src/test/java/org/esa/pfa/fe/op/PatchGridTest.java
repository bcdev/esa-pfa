package org.esa.pfa.fe.op;

import org.geotools.referencing.operation.transform.AffineTransform2D;
import org.junit.Test;
import org.opengis.referencing.operation.MathTransform;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;

import static org.junit.Assert.assertEquals;

/**
 * @author Ralf Quast
 */
public class PatchGridTest {

    final PatchGrid patchGrid = new PatchGrid(0.01, 200);

    @Test
    public void testGetPatchX() throws Exception {
        assertEquals(0, patchGrid.getPatchX(-180.0));

        assertEquals(0, patchGrid.getPatchX(-179.0));

        assertEquals(1, patchGrid.getPatchX(-178.0));

        assertEquals(179, patchGrid.getPatchX(178.0));
    }

    @Test
    public void testGetPatchY() throws Exception {
        assertEquals(0, patchGrid.getPatchY(90.0));

        assertEquals(0, patchGrid.getPatchY(89.0));

        assertEquals(1, patchGrid.getPatchY(88.0));

        assertEquals(89, patchGrid.getPatchY(-88.0));
    }

    @Test
    public void testGetPatchCoordinates() throws Exception {
        final Rectangle r = new Rectangle(0, 0, 200, 200);
        final MathTransform t = new AffineTransform2D(new AffineTransform());

        final Point patchCoordinates = patchGrid.getPatchCoordinates(r, t);
        assertEquals(90, patchCoordinates.x);
        assertEquals(45, patchCoordinates.y);
    }

}

