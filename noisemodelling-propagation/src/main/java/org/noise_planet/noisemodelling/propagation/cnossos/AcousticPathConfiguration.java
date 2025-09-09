package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.algorithm.CGAlgorithms3D;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameter object containing all geometric and processing data for acoustic path construction.
 * Consolidates multiple parameters into a cohesive configuration object to reduce method complexity.
 */
public class AcousticPathConfiguration {
    
    // Geometric data
    private final List<Coordinate> diffractionPoints;
    private final CutProfile cutProfile;
    private final List<CutPoint> cutPoints;
    private final List<Coordinate> cutPointCoordinates2D;
    private final List<Integer> groundEffectPointIndices;
    private final Coordinate[] elevationProfile2D;
    
    // Processing parameters
    private final boolean bodyBarrier;
    private final double groundAttenuationCoefficient;
    private final List<Double> exactFrequencyArray;
    
    // Path computation context
    private final CnossosPath pathParameters;
    private final SegmentPath srPath;

    private final Coordinate sourceCoordinate;
    
    /**
     * Private constructor for builder pattern.
     */
    private AcousticPathConfiguration(Builder builder) {
        this.diffractionPoints = builder.diffractionPoints;
        this.cutProfile = builder.cutProfile;
        this.cutPoints = builder.cutPoints;
        this.cutPointCoordinates2D = builder.cutPointCoordinates2D;
        this.groundEffectPointIndices = builder.groundEffectPointIndices;
        this.elevationProfile2D = builder.elevationProfile2D;
        this.bodyBarrier = builder.bodyBarrier;
        this.groundAttenuationCoefficient = builder.groundAttenuationCoefficient;
        this.exactFrequencyArray = builder.exactFrequencyArray;
        this.pathParameters = builder.pathParameters;
        this.srPath = builder.srPath;
        this.sourceCoordinate = builder.sourceCoordinate;
    }
    
    /**
     * Create a new configuration builder.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create a new configuration builder with values copied from an existing configuration.
     */
    public static Builder builder(AcousticPathConfiguration existing) {
        return new Builder()
                .withDiffractionPoints(existing.diffractionPoints)
                .withCutProfile(existing.cutProfile)
                // .withCutProfilePoints(existing.cutPoints)
                // .withPts2D(existing.cutPointCoordinates2D)
                // .withCut2DGroundIndex(existing.groundEffectPointIndices)
                // .withPts2DGround(existing.elevationProfile2D)
                .withBodyBarrier(existing.bodyBarrier)
                .withGroundAttenuationCoefficient(existing.groundAttenuationCoefficient)
                .withExactFrequencyArray(existing.exactFrequencyArray)
                .withPathParameters(existing.pathParameters)
                .withSrPath(existing.srPath)
                .withSourceCoordinate(existing.sourceCoordinate);
    }

    /**
     * Create a new configuration by copying from an existing one and applying builder modifications.
     */
    public AcousticPathConfiguration copyWith(Builder builder) {
        return builder(this).build();
    }
    
    /**
     * Builder for AcousticPathConfiguration.
     */
    public static class Builder {
        private List<Coordinate> diffractionPoints;
        private CutProfile cutProfile;
        private List<CutPoint> cutPoints;
        private List<Coordinate> cutPointCoordinates2D;
        private List<Integer> groundEffectPointIndices;
        private Coordinate[] elevationProfile2D;
        private boolean bodyBarrier;
        private double groundAttenuationCoefficient;
        private List<Double> exactFrequencyArray;
        private CnossosPath pathParameters;
        private SegmentPath srPath;
        private Coordinate sourceCoordinate;
        
        public Builder withDiffractionPoints(List<Coordinate> diffractionPoints) {
            this.diffractionPoints = diffractionPoints;
            if (diffractionPoints == null) {
                return this;
            }

            this.sourceCoordinate = cutProfile.getSource().getCoordinate();
            if (diffractionPoints.size() < 2) {
                throw new IllegalArgumentException("At least source and receiver points are required.");
            } else if (diffractionPoints.size() > 2) {            
                int startCutPointIndex = cutPointCoordinates2D.indexOf(diffractionPoints.get(0));
                this.sourceCoordinate = cutPointCoordinates2D.get(startCutPointIndex);
            }
            return this;
        }
        
        public Builder withCutProfile(CutProfile cutProfile) {
            this.cutProfile = cutProfile;
            
            if (cutProfile != null) {
                List<CutPoint> cutPoints = cutProfile.getCutPoints();
                List<Coordinate> cutPointCoordinates2D = cutProfile.generateCutPointCoordinates2D();
                
                List<Integer> groundEffectPointIndices = new ArrayList<>(cutPoints.size());
                Coordinate[] elevationProfile2D = cutProfile.generateElevationProfile2D(groundEffectPointIndices).toArray(new Coordinate[0]);

                this.cutPoints = cutPoints;
                this.cutPointCoordinates2D = cutPointCoordinates2D;
                this.groundEffectPointIndices = groundEffectPointIndices;
                this.elevationProfile2D = elevationProfile2D;

                
                // double[] meanPlane = JTSUtility.getMeanPlaneCoefficients(elevationProfile2D);
                
                // SegmentPath srPath = CnossosSegmentComputer.createSegmentPathWithGroundFactors(
                //     cutPointCoordinates2D, 
                //     meanPlane, 
                //     cutProfile.calculateWeightedGroundAbsorption(), 
                //     cutProfile.getGroundAbsorptionAtSource()
                // );
                // srPath.setElevationProfile2D(elevationProfile2D);
                // srPath.setDirectRayDistance(
                //     CGAlgorithms3D.distance(
                //         cutProfile.getReceiver().getCoordinate(),
                //         cutProfile.getSource().getCoordinate()
                //     )
                // );
                // this.srPath = srPath;
            }
            return this;
        }

        
        public Builder withCutProfilePoints(List<CutPoint> cutPoints) {
            this.cutPoints = cutPoints;
            return this;
        }
        
        public Builder withPts2D(List<Coordinate> cutPointCoordinates2D) {
            this.cutPointCoordinates2D = cutPointCoordinates2D;
            return this;
        }
        
        public Builder withCut2DGroundIndex(List<Integer> groundEffectPointIndices) {
            this.groundEffectPointIndices = groundEffectPointIndices;
            return this;
        }
        
        public Builder withPts2DGround(Coordinate[] elevationProfile2D) {
            this.elevationProfile2D = elevationProfile2D;
            return this;
        }
        
        public Builder withBodyBarrier(boolean bodyBarrier) {
            this.bodyBarrier = bodyBarrier;
            return this;
        }
        
        public Builder withGroundAttenuationCoefficient(double groundAttenuationCoefficient) {
            this.groundAttenuationCoefficient = groundAttenuationCoefficient;
            return this;
        }
        
        public Builder withExactFrequencyArray(List<Double> exactFrequencyArray) {
            this.exactFrequencyArray = exactFrequencyArray;
            return this;
        }
        
        public Builder withPathParameters(CnossosPath pathParameters) {
            this.pathParameters = pathParameters;
            return this;
        }
        
        public Builder withSrPath(SegmentPath srPath) {
            this.srPath = srPath;
            return this;
        }
        public Builder withSourceCoordinate(Coordinate sourceCoordinate) {
            this.sourceCoordinate = sourceCoordinate;
            return this;
        }
        
        public AcousticPathConfiguration build() {
            return new AcousticPathConfiguration(this);
        }
    }
    
    // Getters
    public List<Coordinate> getDiffractionPoints() { return diffractionPoints; }
    public CutProfile getCutProfile() { return cutProfile; }
    public List<CutPoint> getCutProfilePoints() { return cutPoints; }
    public List<Coordinate> getPts2D() { return cutPointCoordinates2D; }
    public List<Integer> getCut2DGroundIndex() { return groundEffectPointIndices; }
    public Coordinate[] getPts2DGround() { return elevationProfile2D; }
    public boolean isBodyBarrier() { return bodyBarrier; }
    public double getGroundAttenuationCoefficient() { return groundAttenuationCoefficient; }
    public List<Double> getExactFrequencyArray() { return exactFrequencyArray; }
    public CnossosPath getPathParameters() { return pathParameters; }
    public SegmentPath getSrPath() { return srPath; }

    public Coordinate getCutProfilePointCoordinate(int index) {
        return cutPoints.get(index).getCoordinate();
    }

    public List<Coordinate> getCutPointCoordinates2D() {
        return cutPointCoordinates2D;
    }

    public Coordinate[] getElevationProfile2D() {
        return elevationProfile2D;
    }

    public List<Integer> getGroundEffectPointIndices() {
        return groundEffectPointIndices;
    }

    public Coordinate getSourceCoordinate() {
        return sourceCoordinate;
    }

}
