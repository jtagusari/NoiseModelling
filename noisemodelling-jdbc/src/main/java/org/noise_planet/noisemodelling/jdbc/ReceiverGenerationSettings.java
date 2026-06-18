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
 * Settings for Delaunay triangulation-based receiver point generation.
 * Use {@link Builder} to construct instances.
 */
public class ReceiverGenerationSettings {
    /** Height of generated receivers above ground in metres. Null uses the Z value from the receiver table as-is. */
    private final Double receiverHeight;
    /** Minimum distance from road centrelines at which receivers are placed, in metres. */
    private final double roadBuffer;
    /**
     * Maximum Delaunay triangle area in m², controlling receiver density.
     * Values &lt;= 1 disable mesh refinement (no Steiner points are inserted).
     */
    private final double maximumTriangleArea;
    /** Minimum distance from building footprints at which receivers are placed, in metres. */
    private final double buildingBuffer;
    /** Directory for dumping geometries when a triangulation error occurs. Empty string disables dumping. */
    private final String exceptionDumpFolder;
    /** Snap tolerance when inserting constraint edges into the triangulation, in metres. */
    private final double epsilon;
    /** Douglas-Peucker simplification tolerance applied to input geometries before triangulation, in metres. */
    private final double geometrySimplificationDistance;
    /** When true, iso-surface polygons may extend inside building footprints. */
    private final boolean isoSurfaceInBuildings;


    public ReceiverGenerationSettings(Double receiverHeight, double roadBuffer, double maximumTriangleArea, double buildingBuffer, String exceptionDumpFolder, double epsilon, double geometrySimplificationDistance, boolean isoSurfaceInBuildings) {
        this.receiverHeight = receiverHeight;
        this.roadBuffer = roadBuffer;
        this.maximumTriangleArea = maximumTriangleArea;
        this.buildingBuffer = buildingBuffer;
        this.exceptionDumpFolder = exceptionDumpFolder;
        this.epsilon = epsilon;
        this.geometrySimplificationDistance = geometrySimplificationDistance;
        this.isoSurfaceInBuildings = isoSurfaceInBuildings;
    }

    public static class Builder {
        
        private Double receiverHeight = 4.0;
        private double roadBuffer = 2;
        private double maximumTriangleArea = 75;
        private double buildingBuffer = 2;
        private String exceptionDumpFolder = "";
        private double epsilon = 1e-6;
        private double geometrySimplificationDistance = 1;
        private boolean isoSurfaceInBuildings = false;

        public Builder setReceiverHeight(Double receiverHeight) {
            this.receiverHeight = receiverHeight;
            return this;
        }

        public Builder setRoadBuffer(double roadBuffer) {
            this.roadBuffer = roadBuffer;
            return this;
        }

        public Builder setMaximumTriangleArea(double maximumTriangleArea) {
            this.maximumTriangleArea = maximumTriangleArea;
            return this;
        }

        public Builder setBuildingBuffer(double buildingBuffer) {
            this.buildingBuffer = buildingBuffer;
            return this;
        }

        public Builder setExceptionDumpFolder(String exceptionDumpFolder) {
            this.exceptionDumpFolder = exceptionDumpFolder;
            return this;
        }


        public Builder setEpsilon(double epsilon) {
            this.epsilon = epsilon;
            return this;
        }

        public Builder setGeometrySimplificationDistance(double geometrySimplificationDistance) {
            this.geometrySimplificationDistance = geometrySimplificationDistance;
            return this;
        }

        public Builder setIsoSurfaceInBuildings(boolean isoSurfaceInBuildings) {
            this.isoSurfaceInBuildings = isoSurfaceInBuildings;
            return this;
        }


        public ReceiverGenerationSettings build() {
            ReceiverGenerationSettings settings = new ReceiverGenerationSettings(receiverHeight, roadBuffer, maximumTriangleArea, buildingBuffer, exceptionDumpFolder, epsilon, geometrySimplificationDistance, isoSurfaceInBuildings);
            return settings;
        }
    }


    public Double getReceiverHeight() {
        return receiverHeight;
    }

    public double getRoadBuffer() {
        return roadBuffer;
    }

    public double getMaximumTriangleArea() {
        return maximumTriangleArea;
    }

    public double getBuildingBuffer() {
        return buildingBuffer;
    }

    public String getExceptionDumpFolder() {
        return exceptionDumpFolder;
    }


    public double getEpsilon() {
        return epsilon;
    }

    public double getGeometrySimplificationDistance() {
        return geometrySimplificationDistance;
    }

    public boolean isIsoSurfaceInBuildings() {
        return isoSurfaceInBuildings;
    }
}