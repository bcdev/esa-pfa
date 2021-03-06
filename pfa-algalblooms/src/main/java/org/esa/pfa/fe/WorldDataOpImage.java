package org.esa.pfa.fe;

import com.bc.ceres.core.Assert;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.image.RasterDataNodeSampleOpImage;
import org.esa.snap.core.image.ResolutionLevel;

/**
 * Reads data from a 'world image' in  equirectangular projection.
 *
 * @author Norman Fomferra
 */
public class WorldDataOpImage extends RasterDataNodeSampleOpImage {
    private final GeoCoding geoCoding;
    private final int worldWidth;
    private final int worldHeight;
    private final float[] worldData;

    /**
     * @param geoCoding Source geo-coding
     * @param band  Target band
     * @param level  Resolution level
     * @param worldWidth World image width
     * @param worldHeight World image height
     * @param worldData World image data
     */
    public WorldDataOpImage(GeoCoding geoCoding, Band band, ResolutionLevel level, int worldWidth, int worldHeight, float[] worldData) {
        super(band, level);
        Assert.notNull(geoCoding, "geoCoding");
        Assert.notNull(band, "band");
        Assert.notNull(level, "level");
        Assert.notNull(worldData, "worldData");
        this.geoCoding = geoCoding;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.worldData = worldData;
    }

    @Override
    protected double computeSample(int sourceX, int sourceY) {
        GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(sourceX + 0.5f, sourceY + 0.5f), null);
        int vx = (int)Math.round(worldWidth * (geoPos.lon + 180.0f) / 360.0f);
        int vy = (int)Math.round(worldHeight * (90.0f - geoPos.lat) / 180.0f);
        if (vx < 0) {
            vx = 0;
        }
        if (vx >= worldWidth) {
            vx = worldWidth - 1;
        }
        if (vy < 0) {
            vy = 0;
        }
        if (vy >= worldHeight) {
            vy = worldHeight - 1;
        }
        return worldData[vy * worldWidth + vx];
    }
}
