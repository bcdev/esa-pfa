package org.esa.pfa.fe.spectral;

import org.esa.snap.framework.datamodel.GeoPos;

/**
 * Created by Norman on 07.07.2015.
 */
public class GlobalPatchCS {

    private double resolution;

    public GlobalPatchCS(double resolution) {
        this.resolution = resolution;
    }

    public double getLowerBoundGeoPos(double v) {
        return Math.floor(v / resolution) * resolution;
    }

    public double getUpperBoundCoordinate(double v) {
        return Math.floor((v + resolution) / resolution) * resolution;
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
