/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc;

/**
 * Controls how the computation is executed: domain decomposition and geometry pre-processing
 * for memory efficiency. These parameters do not affect the physical propagation model.
 * Use {@link Builder} to construct instances.
 */
public class ComputationSettings {

    /**
     * Number of grid cells per side used to split the computation domain.
     * 0 means auto-compute from envelope size and propagation distance.
     */
    private final int gridDim;

    /**
     * Side length used to subdivide large ground-absorption polygons into tiles before
     * intersection, in metres. Smaller values reduce per-cell memory usage at the cost
     * of more intersection operations.
     */
    private final double groundSurfaceSplitSideLength;

    public ComputationSettings(int gridDim, double groundSurfaceSplitSideLength) {
        this.gridDim = gridDim;
        this.groundSurfaceSplitSideLength = groundSurfaceSplitSideLength;
    }

    public static class Builder {
        private int gridDim = 0;
        private double groundSurfaceSplitSideLength = 200;

        public Builder setGridDim(int gridDim) {
            this.gridDim = gridDim;
            return this;
        }

        public Builder setGroundSurfaceSplitSideLength(double groundSurfaceSplitSideLength) {
            this.groundSurfaceSplitSideLength = groundSurfaceSplitSideLength;
            return this;
        }

        public ComputationSettings build() {
            return new ComputationSettings(gridDim, groundSurfaceSplitSideLength);
        }
    }

    public int getGridDim() {
        return gridDim;
    }

    public double getGroundSurfaceSplitSideLength() {
        return groundSurfaceSplitSideLength;
    }
}
