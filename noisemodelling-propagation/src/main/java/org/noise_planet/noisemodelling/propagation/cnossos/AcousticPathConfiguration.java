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
    private final List<Coordinate> horizontalEdgePivotPoints;
    private final CutProfile cutProfile;
    private final List<CutPoint> cutPoints;
    private final List<Coordinate> cutPointCoordinates2D;
    private Coordinate sourceCoordinate2D;
    private Coordinate receiverCoordinate2D;
    private final List<Integer> cutPointExpandedIndices;
    private final Coordinate[] elevationProfile2D;
    
    // Processing parameters
    private final boolean bodyBarrier;
    private final double groundAttenuationCoefficient;
    private final List<Double> exactFrequencyArray;
    
    /**
     * Private constructor for builder pattern.
     */
    private AcousticPathConfiguration(Builder builder) {
        this.horizontalEdgePivotPoints = builder.horizontalEdgePivotPoints;
        this.cutProfile = builder.cutProfile;
        this.cutPoints = builder.cutPoints;
        this.cutPointCoordinates2D = builder.cutPointCoordinates2D;
        this.sourceCoordinate2D = builder.sourceCoordinate2D;
        this.receiverCoordinate2D = builder.receiverCoordinate2D;
        this.cutPointExpandedIndices = builder.cutPointExpandedIndices;
        this.elevationProfile2D = builder.elevationProfile2D;
        this.bodyBarrier = builder.bodyBarrier;
        this.groundAttenuationCoefficient = builder.groundAttenuationCoefficient;
        this.exactFrequencyArray = builder.exactFrequencyArray;
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
                .withHorizontalEdgePivotPoints(existing.horizontalEdgePivotPoints)
                .withCutProfile(existing.cutProfile)
                .withBodyBarrier(existing.bodyBarrier)
                .withGroundAttenuationCoefficient(existing.groundAttenuationCoefficient)
                .withExactFrequencyArray(existing.exactFrequencyArray);
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
        private List<Coordinate> horizontalEdgePivotPoints;
        private CutProfile cutProfile;
        private List<CutPoint> cutPoints;
        private List<Coordinate> cutPointCoordinates2D;
        private Coordinate sourceCoordinate2D;
        private Coordinate receiverCoordinate2D;
        private List<Integer> cutPointExpandedIndices;
        private Coordinate[] elevationProfile2D;
        private boolean bodyBarrier;
        private double groundAttenuationCoefficient;
        private List<Double> exactFrequencyArray;
        
        public Builder withHorizontalEdgePivotPoints(List<Coordinate> horizontalEdgePivotPoints) {
            this.horizontalEdgePivotPoints = horizontalEdgePivotPoints;
            if (horizontalEdgePivotPoints == null) {
                return this;
            }

            // this.sourceCoordinate = cutProfile.getSource().getCoordinate();
            if (horizontalEdgePivotPoints.size() < 2) {
                throw new IllegalArgumentException("At least source and receiver points are required.");
            } 
            return this;
        }
        
        public Builder withCutProfile(CutProfile cutProfile) {
            this.cutProfile = cutProfile;
            
            if (cutProfile != null) {
                List<CutPoint> cutPoints = cutProfile.getCutPoints();
                List<Coordinate> cutPointCoordinates2D = cutProfile.generateCutPointCoordinates2D();
                
                List<Integer> cutPointExpandedIndices = new ArrayList<>(cutPoints.size());
                Coordinate[] elevationProfile2D = cutProfile.generateElevationProfile2D(cutPointExpandedIndices).toArray(new Coordinate[0]);

                this.cutPoints = cutPoints;
                this.cutPointCoordinates2D = cutPointCoordinates2D;
                this.sourceCoordinate2D = cutPointCoordinates2D.get(0);
                this.receiverCoordinate2D = cutPointCoordinates2D.get(cutPointCoordinates2D.size() - 1);
                this.cutPointExpandedIndices = cutPointExpandedIndices;
                this.elevationProfile2D = elevationProfile2D;

                if (cutPointCoordinates2D.size() != cutPoints.size()) {
                    throw new IllegalArgumentException("The two arrays (cutPoint and cutPointCoordinates2D) size should be the same");
                }
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
        
        public Builder withCut2DGroundIndex(List<Integer> cutPointExpandedIndices) {
            this.cutPointExpandedIndices = cutPointExpandedIndices;
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
        
                
        public AcousticPathConfiguration build() {
            return new AcousticPathConfiguration(this);
        }
    }
    
    // Getters
    public List<Coordinate> getHorizontalEdgePivotPoints() { return horizontalEdgePivotPoints; }
    public CutProfile getCutProfile() { return cutProfile; }
    public List<CutPoint> getCutProfilePoints() { return cutPoints; }
    public List<Coordinate> getPts2D() { return cutPointCoordinates2D; }
    public List<Integer> getCut2DGroundIndex() { return cutPointExpandedIndices; }
    public Coordinate[] getPts2DGround() { return elevationProfile2D; }
    public boolean isBodyBarrier() { return bodyBarrier; }
    public double getGroundAttenuationCoefficient() { return groundAttenuationCoefficient; }
    public List<Double> getExactFrequencyArray() { return exactFrequencyArray; }


    public Coordinate getCutProfilePointCoordinate(int index) {
        return cutPoints.get(index).getCoordinate();
    }

    public List<Coordinate> getCutPointCoordinates2D() {
        return cutPointCoordinates2D;
    }

    public Coordinate[] getElevationProfile2D() {
        return elevationProfile2D;
    }

    public List<Integer> getCutPointExpandedIndices() {
        return cutPointExpandedIndices;
    }

    public Coordinate getSourceCoordinate2D() {
        return sourceCoordinate2D;
    }

    public Coordinate getReceiverCoordinate2D() {
        return receiverCoordinate2D;
    }

}
