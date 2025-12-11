package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.geom.Coordinate;

public class PivotPoint extends Coordinate {
    public enum PivotType {
        SOURCE,
        RECEIVER,
        TOP_OF_OBSTACLE,
        BOTTOM_OF_OBSTACLE
    }

    private PivotType pivotType;

    public PivotPoint(Coordinate coord, PivotType pivotType) {
        super(coord);
        this.pivotType = pivotType;
    }

    public PivotPoint(double x, double y, PivotType pivotType) {
        super(x, y);
        this.pivotType = pivotType;
    }

    public PivotType getPivotType() {
        return pivotType;
    }

    public void setPivotType(PivotType pivotType) {
        this.pivotType = pivotType;
    }
}
